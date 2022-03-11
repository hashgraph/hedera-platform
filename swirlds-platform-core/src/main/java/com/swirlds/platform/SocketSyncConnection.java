/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.io.BadIOException;
import com.swirlds.platform.sync.SyncConnectionState;
import com.swirlds.platform.sync.SyncConstants;
import com.swirlds.platform.sync.SyncException;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_CONNECTION;
import static com.swirlds.logging.LogMarker.TCP_CONNECT_EXCEPTIONS;

/**
 * Manage a single connection with another member, which can be initiated by self or by them. Once the
 * connection is established, it can be used for syncing, and will have heartbeats that keep it alive.
 */
public class SocketSyncConnection implements SyncConnection {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	private static final byte[] LOCALHOST = new byte[] { 127, 0, 0, 1 };

	private final NodeId selfId;
	private final NodeId otherId;
	private final SyncInputStream dis;
	private final SyncOutputStream dos;
	private final Socket socket;
	private final AbstractPlatform platform;
	private final AtomicBoolean connected = new AtomicBoolean(true);
	private final boolean outbound;
	private final String description;
	/** the current state of this connection */
	private final AtomicReference<SyncConnectionState> currentState;

	/**
	 * return the total number of bytes written to all the given connections since the last time this was
	 * called. If a connection was closed and recreated, it will NOT include the bytes sent to the old
	 * connection. It only includes the current connections.
	 *
	 * @return the number of bytes written
	 */
	static long getBytesWrittenSinceLast(final Platform platform,
			final AtomicReferenceArray<SocketSyncConnection> connArray) {
		long result = 0;
		if (connArray != null) {
			for (int i = 0; i < connArray.length(); i++) {
				final SocketSyncConnection conn = connArray.get(i);
				if (conn != null) {
					final long bytesSent = conn.getBytesWrittenSinceLast();
					result += bytesSent;
					((com.swirlds.platform.Statistics) platform.getStats()).avgBytePerSecSent[i].update(bytesSent);
				}
			}
		}
		return result;
	}

	/**
	 * Return the number of bytes written to the DataOutputStream since the last time this method was
	 * called. On the first call, it will be the number since the connection was first created.
	 *
	 * If more than 2 billion bytes have been written, then this may give an incorrect result.
	 *
	 * @return the number of bytes written since the last call
	 */
	long getBytesWrittenSinceLast() {
		return (dos == null) ? 0 : dos.getConnectionByteCounter().getAndResetCount();
	}

	@Override
	public NodeId getSelfId() {
		return selfId;
	}

	@Override
	public NodeId getOtherId() {
		return otherId;
	}

	@Override
	public SyncInputStream getDis() {
		return dis;
	}

	@Override
	public SyncOutputStream getDos() {
		return dos;
	}

	public Socket getSocket() {
		return socket;
	}

	/**
	 * Sets the timeout of the underlying socket using {@link Socket#setSoTimeout(int)}
	 *
	 * @param timeoutMillis
	 * 		the timeout value to set in milliseconds
	 * @throws SocketException
	 * 		if there is an error in the underlying protocol, such as a TCP error.
	 */
	@Override
	public void setTimeout(final int timeoutMillis) throws SocketException {
		socket.setSoTimeout(timeoutMillis);
	}

	@Override
	public int getTimeout() throws SocketException {
		return socket.getSoTimeout();
	}

	/**
	 * End this connection by closing the socket and streams, and setting them to null. Also update the
	 * statistics for the caller (if caller is true) or listener (if false).
	 *
	 * @param caller
	 * 		true if it is a caller or heartbeat thread (but not a listener thread) doing this
	 */
	@Override
	public void disconnect(final boolean caller, final int debug) {
		final boolean wasConnected = connected.getAndSet(false);
		if (wasConnected) {
			platform.connectionClosed();
		}
		LOG.debug(SYNC.getMarker(),
				"disconnecting connection from {} to {}, reason: {}", selfId, otherId, debug);
		if (connected()) {
			// only update the stats etc when closing an open connection. Not when closing the same twice.
			if (caller) {
				platform.getStats().interruptedCallSyncsPerSecond.cycle();
			} else {
				platform.getStats().interruptedRecSyncsPerSecond.cycle();
			}
		}

		close(socket);
		close(dis);
		close(dos);
		currentState.set(SyncConnectionState.DISCONNECTED);
	}

	private static void close(final Closeable closeable) {
		try {
			closeable.close();
		} catch (Exception e) {
			// Intentionally suppressing all exceptions
		}
	}

	/**
	 * @param platform
	 * 		the platform running this hashgraph
	 * @param selfId
	 * 		the ID number of the local member
	 * @param otherId
	 * 		the ID number of the other member
	 * @param outbound
	 * 		is the connection outbound
	 * @param socket
	 * 		the socket connecting the two members over TCP/IP
	 * @param dis
	 * 		the input stream
	 * @param dos
	 * 		the output stream
	 */
	public SocketSyncConnection(
			final NodeId selfId,
			final NodeId otherId,
			final AbstractPlatform platform,
			final boolean outbound,
			final Socket socket,
			final SyncInputStream dis,
			final SyncOutputStream dos) {
		this.selfId = selfId;
		this.otherId = otherId;
		this.platform = platform;
		this.outbound = outbound;
		this.description = generateDescription();

		this.socket = socket;
		this.dis = dis;
		this.dos = dos;

		this.currentState = new AtomicReference<>(SyncConnectionState.NEW_CONNECTION);
		platform.newConnectionOpened();
	}

	/**
	 * Try to connect to the member with the given ID. If it doesn't work on the first try, give up
	 * immediately. Return the connection, or null if it didn't connect.
	 *
	 * @param platform
	 * 		the platform calling this method
	 * @param selfId
	 * 		my member Id (the one initiating the connection)
	 * @param otherId
	 * 		which member to connect to
	 * @return the new connection, or null if it couldn't connect on the first try
	 */
	static SocketSyncConnection connect(final AbstractPlatform platform, final NodeId selfId, final NodeId otherId) {
		LOG.debug(SYNC_CONNECTION.getMarker(),
				"`connect` : {} about to connect to {}",
				platform.getSelfId(),
				otherId);

		if (!selfId.sameNetwork(otherId)) {
			throw new IllegalArgumentException("Must connect to node on the same network!");
		}
		final AddressBook addressBook;
		if (selfId.isMain()) {
			addressBook = platform.getAddressBook();
		} else {
			throw new UnsupportedOperationException("Not yet implemented!");
		}

		final Address other = addressBook.getAddress(otherId.getId());
		final Address ownAddress = addressBook.getAddress(selfId.getId());
		final int port = other.getConnectPortIpv4(ownAddress);
		final byte[] ip = getConnectAddressIpv4(ownAddress, other);
		final String ipAddress = Address.ipString(ip);

		Socket clientSocket;
		SyncOutputStream dos;
		SyncInputStream dis;

		try {
			clientSocket = platform.getSocketFactory().createClientSocket(ipAddress, port);

			dos = SyncOutputStream.createSyncOutputStream(clientSocket.getOutputStream(), Settings.bufferSize);
			dis = SyncInputStream.createSyncInputStream(clientSocket.getInputStream(), Settings.bufferSize);

			dos.writeUTF(
					addressBook.getAddress(platform.getSelfId().getId()).getNickname());
			dos.flush();

			final int ack = dis.readInt(); // read the ACK for creating the connection
			if (ack != SyncConstants.COMM_CONNECT) {  // this is an ACK for creating the connection
				clientSocket.close();
				dos.close();
				dis.close();
				throw new ConnectException("ack is not " + SyncConstants.COMM_CONNECT
						+ ", it is " + ack);
			}
			LOG.debug(SYNC_CONNECTION.getMarker(),
					"`connect` : finished, {} connected to {}",
					platform.getSelfId(),
					otherId);


			return new SocketSyncConnection(
					selfId,
					otherId,
					platform,
					true,
					clientSocket,
					dis,
					dos
			);
		} catch (final SocketTimeoutException | SocketException e) {
			LOG.debug(TCP_CONNECT_EXCEPTIONS.getMarker(),
					"{} failed to connect to {} with error:", platform.getSelfId(), otherId, e);
			// ConnectException (which is a subclass of SocketException) happens when calling someone
			// who isn't running yet. So don't worry about it.
			// Also ignore the other socket-related errors (SocketException) in case it times out while
			// connecting.
		} catch (final SSLException e) {
			// log the SSL connection exception which is caused by socket exceptions as warning.
			LOG.warn(SOCKET_EXCEPTIONS.getMarker(),
					"{} failed to connect to {} with SSLException:", platform.getSelfId(), otherId, e);
		} catch (final Exception e) { // java.net.SocketTimeoutException or IOException if connection times
			// out
			LOG.debug(EXCEPTION.getMarker(),
					"{} SyncCaller.sync failed to connect to {} with error:", platform.getSelfId(), otherId, e);
		}

		LOG.debug(SYNC_CONNECTION.getMarker(),
				"`connect` : finished, {} did not connect to {}",
				platform.getSelfId(),
				otherId);

		return null;
	}

	/**
	 * Find the best way to connect <code>from</code> address <code>to</code> address
	 *
	 * @param from
	 * 		the address that needs to connect
	 * @param to
	 * 		the address to connect to
	 * @return the IP address to connect to
	 */
	private static byte[] getConnectAddressIpv4(final Address from, final Address to) {
		if (from.isOwnHost() && to.isOwnHost() && Settings.useLoopbackIp) {
			return LOCALHOST;
		} else if (to.isLocalTo(from)) {
			return to.getAddressInternalIpv4();
		} else {
			return to.getAddressExternalIpv4();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean connected() {
		try {
			if (socket != null && !socket.isClosed() && socket.isBound()
					&& socket.isConnected() && dis != null && dos != null) {
				return true; // good connection
			}
		} catch (final Exception e) {
			LOG.error(EXCEPTION.getMarker(),
					"SyncConnection.connected error on connection from {} to {}", selfId, otherId, e);
		}
		return false; // bad connection
	}

	@Override
	public void initForSync() throws IOException {
		if (!this.connected()) {
			throw new BadIOException("not a valid connection ");
		}

		// conn.connected() was true above, but maybe it became false right after the check so dis or dos
		// is null.
		if (socket == null || this.getDis() == null || this.getDos() == null) {
			throw new BadIOException("not a valid connection ");
		}

		/* track the number of bytes written and read during a sync */
		getDis().getSyncByteCounter().resetCount();
		getDos().getSyncByteCounter().resetCount();

		this.setTimeout(Settings.timeoutSyncClientSocket);
		currentState.set(SyncConnectionState.SYNC_STARTED);
	}

	@Override
	public void syncDone() {
		currentState.set(SyncConnectionState.SYNC_ENDED);
	}

	@Override
	public boolean isOutbound() {
		return outbound;
	}

	@Override
	public String getDescription() {
		return description;
	}


	/**
	 * @return the current state of this connection, never null
	 */
	public SyncConnectionState getCurrentState() {
		return currentState.get();
	}

	/**
	 * Sends and receives a heartbeat
	 *
	 * @throws IOException
	 * 		if a problem with the connection occurs
	 * @throws SyncException
	 * 		if we don't receive a HEARTBEAT_ACK
	 */
	public void heartbeat() throws IOException, SyncException {
		dos.write(SyncConstants.HEARTBEAT);
		currentState.set(SyncConnectionState.HEARTBEAT_SENT);
		dos.flush();
		getSocket().setSoTimeout(Settings.timeoutSyncClientSocket);
		final byte b = dis.readByte();
		if (b != SyncConstants.HEARTBEAT_ACK) {
			throw new SyncException(String.format(
					"received %02x but expected %02x (heartbeatACK)",
					b, SyncConstants.HEARTBEAT_ACK));
		}
		currentState.set(SyncConnectionState.HEARTBEAT_ACKNOWLEDGED);
	}
}

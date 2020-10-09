/*
 * (c) 2016-2020 Swirlds, Inc.
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
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.SYNC_START;
import static com.swirlds.logging.LogMarker.TCP_CONNECT_EXCEPTIONS;

/**
 * Manage a single connection with another member, which can be initiated by self or by them. Once the
 * connection is established, it can be used for syncing, and will have heartbeats that keep it alive.
 */
public class SyncConnection {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	private static final byte[] LOCALHOST = new byte[] { 127, 0, 0, 1 };

	private NodeId selfId = null;
	private NodeId otherId = null;
	private SyncInputStream dis = null;
	private SyncOutputStream dos = null;
	private Socket socket = null;
	private AbstractPlatform platform = null;
	private AtomicBoolean connected = new AtomicBoolean(true);

	SyncShadowGraphManager getSyncShadowGraphManager() {
		return platform.getSyncShadowGraphManager();
	}

	/**
	 * return the total number of bytes written to all the given connections since the last time this was
	 * called. If a connection was closed and recreated, it will NOT include the bytes sent to the old
	 * connection. It only includes the current connections.
	 *
	 * @return the number of bytes written
	 */
	static long getBytesWrittenSinceLast(Platform platform,
			AtomicReferenceArray<SyncConnection> connArray) {
		long result = 0;
		if (connArray != null) {
			for (int i = 0; i < connArray.length(); i++) {
				SyncConnection conn = connArray.get(i);
				if (conn != null) {
					long bytesSent = conn.getBytesWrittenSinceLast();
					result += bytesSent;
					((com.swirlds.platform.Statistics)platform.getStats()).avgBytePerSecSent[i].update(bytesSent);
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

	public NodeId getSelfId() {
		return selfId;
	}

	public NodeId getOtherId() {
		return otherId;
	}

	public SyncInputStream getDis() {
		return dis;
	}

	public SyncOutputStream getDos() {
		return dos;
	}

	public Socket getSocket() {
		return socket;
	}

	public AbstractPlatform getPlatform() {
		return platform;
	}

	/**
	 * End this connection by closing the socket and streams, and setting them to null. Also update the
	 * statistics for the caller (if caller is true) or listener (if false).
	 *
	 * @param caller
	 * 		true if it is a caller or heartbeat thread (but not a listener thread) doing this
	 */
	void disconnect(boolean caller, int debug) {
		boolean wasConnected = connected.getAndSet(false);
		if (wasConnected) {
			platform.connectionClosed();
		}
		log.debug(SYNC.getMarker(),
				"disconnecting connection from {} to {}, reason: {}", selfId, otherId, debug);
		if (connected()) {
			// only update the stats etc when closing an open connection. Not when closing the same twice.
			if (caller) {
				platform.getStats().interruptedCallSyncsPerSecond.cycle();
			} else {
				platform.getStats().interruptedRecSyncsPerSecond.cycle();
			}
		}

		try {
			socket.close();
		} catch (Exception e) {
		}
		try {
			dis.close();
		} catch (Exception e) {
		}
		try {
			dos.close();
		} catch (Exception e) {
		}
		socket = null;
		dis = null;
		dos = null;
		selfId = otherId = null;
	}

	/**
	 * record a connection that was just created
	 *
	 * @param platform
	 * 		the platform running this hashgraph
	 * @param selfId
	 * 		the ID number of the local member
	 * @param otherId
	 * 		the ID number of the other member
	 * @param socket
	 * 		the socket connecting the two members over TCP/IP
	 * @param dis
	 * 		the input stream
	 * @param dos
	 * 		the output stream
	 */
	void set(AbstractPlatform platform, NodeId selfId, NodeId otherId, Socket socket,
			SyncInputStream dis, SyncOutputStream dos) {
		this.platform = platform;
		this.selfId = selfId;
		this.otherId = otherId;
		this.socket = socket;
		this.dis = dis;
		this.dos = dos;

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
	static SyncConnection connect(
		AbstractPlatform platform,
		NodeId selfId, NodeId otherId)
	{
		log.debug(SYNC_START.getMarker(), "{} about to connect to {}",
				platform.getSelfId(), otherId);

		if (!selfId.sameNetwork(otherId)) {
			throw new IllegalArgumentException("Must connect to node on the same network!");
		}
		AddressBook addressBook;
		if (selfId.isMain()) {
			addressBook = platform.getHashgraph().getAddressBook();
		} else {
			throw new RuntimeException("Not yet implemented!");
		}

		Address other = addressBook.getAddress(otherId.getId());
		Address ownAddress = addressBook.getAddress(selfId.getId());
		int port = other.getConnectPortIpv4(ownAddress);
		byte[] ip = getConnectAddressIpv4(ownAddress, other);
		String ipAddress = Address.ipString(ip);

		Socket clientSocket = null;
		SyncOutputStream dos = null;
		SyncInputStream dis = null;

		try {
			clientSocket = platform.getCrypto().newClientSocketConnect(ipAddress,
					port);
			dos = SyncOutputStream.createSyncOutputStream(clientSocket.getOutputStream(), Settings.bufferSize);
			dis = SyncInputStream.createSyncInputStream(clientSocket.getInputStream(), Settings.bufferSize);

			dos.writeUTF(
					addressBook.getAddress(platform.getSelfId().getId()).getNickname());
			dos.flush();

			int ack = dis.readInt(); // read the ACK for creating the connection
			if (ack != SyncConstants.commConnect) {  // this is an ACK for creating the connection
				clientSocket.close();
				dos.close();
				dis.close();
				throw new Exception("ack is not " + SyncConstants.commConnect
						+ ", it is " + ack);
			}

			if (clientSocket != null && dis != null) { // set all 3 or none at all
				log.debug(SYNC_START.getMarker(), "{} connected to {}",
						platform.getSelfId(), otherId);
				SyncConnection sc = new SyncConnection();
				sc.set(platform, selfId, otherId, clientSocket, dis, dos);
				return sc;
			} else {
				log.debug(TCP_CONNECT_EXCEPTIONS.getMarker(),
						"{} failed to connect to {} but with no error",
						platform.getSelfId(), otherId);
			}
		} catch (SocketTimeoutException | SocketException e) {
			log.debug(TCP_CONNECT_EXCEPTIONS.getMarker(),
					"{} failed to connect to {} with error:", platform.getSelfId(), otherId, e);
			// ConnectException (which is a subclass of SocketException) happens when calling someone
			// who
			// isn't running yet. So don't worry about it.
			// Also ignore the other socket-related errors (SocketException) in case it times out while
			// connecting.
		} catch (SSLException e) {
			// log the SSL connection exception which is caused by socket exceptions as warning.
			log.warn(SOCKET_EXCEPTIONS.getMarker(),
					"{} failed to connect to {} with SSLException:", platform.getSelfId(), otherId, e);
		} catch (Exception e) { // java.net.SocketTimeoutException or IOException if connection times
			// out
			log.debug(EXCEPTION.getMarker(),
					"{} SyncCaller.sync failed to connect to {} with error:", platform.getSelfId(), otherId, e);
		}
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
	private static byte[] getConnectAddressIpv4(Address from, Address to) {
		return (from.isOwnHost() && to.isOwnHost() && Settings.useLoopbackIp)
				? LOCALHOST
				: (to.isLocalTo(from) ? to.getAddressInternalIpv4()
				: to.getAddressExternalIpv4());
	}

	/**
	 * Wait for a new incoming connection, and return it.
	 *
	 * @param serverSocket
	 * 		the ServerSocket that should accept the connection request
	 * @param timeout
	 * 		return null after this many milliseconds, if no connection is received
	 * @return the new connection that was created
	 */
	static SyncConnection acceptConnection(AbstractPlatform platform,
			ServerSocket serverSocket, long timeout) {
		String otherKey = "";
		SyncInputStream dis = null;
		SyncOutputStream dos = null;
		NodeId otherId = null;
		Socket clientSocket = null;
		long acceptTime = 0;
		int conId = -1;// random 4-digit identifier for the connection
		try {
			serverSocket.setSoTimeout(Settings.timeoutServerAcceptConnect);
			clientSocket = serverSocket.accept(); // listen, waiting until someone connects
			acceptTime = System.currentTimeMillis();
			clientSocket.setTcpNoDelay(Settings.tcpNoDelay);
			clientSocket.setSoTimeout(Settings.timeoutSyncClientSocket);
			dis = SyncInputStream.createSyncInputStream(clientSocket.getInputStream(), Settings.bufferSize);

			dos = SyncOutputStream.createSyncOutputStream(clientSocket.getOutputStream(), Settings.bufferSize);

			otherKey = dis.readUTF();

			otherId = new NodeId(false, platform.getHashgraph().getAddressBook().getId(otherKey));

			dos.writeInt(conId);// send a connection ID number (chosen randomly)
			dos.writeInt(SyncConstants.commConnect);// send an ACK for creating connection
			dos.flush();

			// ignore invalid IDs, but store the streams for valid ones
			if (otherId.getId() >= 0
					&& otherId.getId() < platform.getHashgraph().getAddressBook().getSize()
					&& platform.getConnectionGraph()
					.isAdjacent(platform.getSelfId().getIdAsInt(), otherId.getIdAsInt())) {
				log.debug(SYNC_START.getMarker(),
						"listener {} just established connection initiated by {}",
						platform.getSelfId(), otherId);
				SyncConnection sc = new SyncConnection();
				sc.set(platform, platform.getSelfId(), otherId, clientSocket, dis, dos);
				return sc;
			} else {
				close1(dis, dos, clientSocket);
				return null;
			}
		} catch (SocketTimeoutException expectedWithNonZeroSOTimeout) {
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(),
					"SyncConnection.acceptConnection() error, remote IP: {}\nTime from accept to exception: {} ms\n",
					clientSocket.getInetAddress().toString(),
					acceptTime == 0 ? "N/A" : (System.currentTimeMillis() - acceptTime), e);
			log.error(SYNC.getMarker(), "Listener {} hearing {} had general Exception:",
					platform.getSelfId(), otherId, e);
			close1(dis, dos, clientSocket);
		}
		return null;
	}

	private static void close1(DataInputStream dis, DataOutputStream dos,
			Socket clientSocket) {
		try {
			dis.close();
		} catch (Exception e) {
		}
		try {
			dos.close();
		} catch (Exception e) {
		}
		try {
			clientSocket.close();
		} catch (Exception e) {
		}
	}

	/**
	 * Is there currently a valid connection from me to the member at the given index in the address book?
	 *
	 * @return are we connected?
	 */
	boolean connected() {
		try {
			if (socket != null && !socket.isClosed() && socket.isBound()
					&& socket.isConnected() && dis != null && dos != null) {
				return true; // good connection
			}
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(),
					"SyncConnection.connected error on connection from {} to {}", selfId, otherId, e);
		}
		return false; // bad connection
	}

	/**
	 * Is there currently a valid connection from me to the member at the given index in the address book?
	 *
	 * @return are we connected?
	 */
	static boolean connection(SyncConnection syncConnection) {
		return syncConnection != null && syncConnection.connected();
	}

}

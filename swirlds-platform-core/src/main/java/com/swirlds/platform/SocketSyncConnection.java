/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import com.swirlds.common.NodeId;
import com.swirlds.common.io.BadIOException;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.sync.SyncInputStream;
import com.swirlds.platform.sync.SyncOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SYNC;

/**
 * Manage a single connection with another member, which can be initiated by self or by them. Once the
 * connection is established, it can be used for syncing, and will have heartbeats that keep it alive.
 */
public class SocketSyncConnection implements SyncConnection {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	private final NodeId selfId;
	private final NodeId otherId;
	private final SyncInputStream dis;
	private final SyncOutputStream dos;
	private final Socket socket;
	private final ConnectionTracker connectionTracker;
	private final AtomicBoolean connected = new AtomicBoolean(true);
	private final boolean outbound;
	private final String description;

	/**
	 * @param connectionTracker
	 * 		tracks open connections
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
	protected SocketSyncConnection(
			final NodeId selfId,
			final NodeId otherId,
			final ConnectionTracker connectionTracker,
			final boolean outbound,
			final Socket socket,
			final SyncInputStream dis,
			final SyncOutputStream dos) {
		Objects.requireNonNull(socket);
		Objects.requireNonNull(dis);
		Objects.requireNonNull(dos);

		this.selfId = selfId;
		this.otherId = otherId;
		this.connectionTracker = connectionTracker;
		this.outbound = outbound;
		this.description = generateDescription();

		this.socket = socket;
		this.dis = dis;
		this.dos = dos;
	}

	/**
	 * Creates a new connection instance
	 *
	 * @param connectionTracker
	 * 		tracks open connections
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
	public static SocketSyncConnection create(
			final NodeId selfId,
			final NodeId otherId,
			final ConnectionTracker connectionTracker,
			final boolean outbound,
			final Socket socket,
			final SyncInputStream dis,
			final SyncOutputStream dos) {
		final SocketSyncConnection c =
				new SocketSyncConnection(selfId, otherId, connectionTracker, outbound, socket, dis, dos);
		connectionTracker.newConnectionOpened(c);
		return c;
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

	@Override
	public int getTimeout() throws SocketException {
		return socket.getSoTimeout();
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

	/**
	 * End this connection by closing the socket and streams
	 */
	@Override
	public void disconnect() {
		final boolean wasConnected = connected.getAndSet(false);
		if (wasConnected) {
			// only update when closing an open connection. Not when closing the same twice.
			connectionTracker.connectionClosed(isOutbound());
		}
		LOG.debug(SYNC.getMarker(),
				"disconnecting connection from {} to {}", selfId, otherId);

		NetworkUtils.close(socket, dis, dos);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean connected() {
		try {
			if (!socket.isClosed() && socket.isBound() && socket.isConnected()) {
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

		/* track the number of bytes written and read during a sync */
		getDis().getSyncByteCounter().resetCount();
		getDos().getSyncByteCounter().resetCount();

		this.setTimeout(Settings.timeoutSyncClientSocket);
	}

	@Override
	public boolean isOutbound() {
		return outbound;
	}

	@Override
	public String getDescription() {
		return description;
	}
}

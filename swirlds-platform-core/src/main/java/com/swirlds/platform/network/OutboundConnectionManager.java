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

package com.swirlds.platform.network;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.locks.LockedResource;
import com.swirlds.common.threading.locks.ResourceLock;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a connection that is initiated by this node. If the connection in use is broken, it will try to establish a
 * new one.
 */
public class OutboundConnectionManager implements ConnectionManager {
	private final NodeId peerId;
	private final OutboundConnectionCreator connectionCreator;
	/** the current connection in use, initially not connected. there is no synchronization on this variable */
	private SyncConnection currentConn = NotConnectedConnection.getSingleton();
	/** locks the connection managed by this instance */
	private final ResourceLock<SyncConnection> lock = new ResourceLock<>(new ReentrantLock(), currentConn);

	public OutboundConnectionManager(
			final NodeId peerId,
			final OutboundConnectionCreator connectionCreator) {
		this.peerId = peerId;
		this.connectionCreator = connectionCreator;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SyncConnection waitForConnection() {
		try (final LockedResource<SyncConnection> resource = lock.lock()) {
			while (!resource.getResource().connected()) {
				resource.getResource().disconnect();
				resource.setResource(connectionCreator.createConnection(peerId));
			}
			currentConn = resource.getResource();
		}
		return currentConn;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SyncConnection getConnection() {
		return currentConn;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newConnection(final SyncConnection connection) {
		throw new UnsupportedOperationException("Does not accept connections");
	}
}

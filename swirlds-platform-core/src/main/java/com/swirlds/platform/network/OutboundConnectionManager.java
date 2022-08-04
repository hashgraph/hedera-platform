/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

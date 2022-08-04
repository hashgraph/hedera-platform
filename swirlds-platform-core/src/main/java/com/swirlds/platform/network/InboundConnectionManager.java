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

import com.swirlds.common.threading.locks.LockedResource;
import com.swirlds.common.threading.locks.ResourceLock;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.connection.NotConnectedConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;

/**
 * Manages a connection that is initiated by the peer. If a new connection is established by the peer, the previous one
 * will be closed.
 *
 * Calls to the method {@link #newConnection(SyncConnection)} are thread safe. Calls to {@link #waitForConnection()} and
 * {@link #getConnection()} are not. It is assumed that these methods will either be always called by the same thread, or
 * there should be some external synchronization on them.
 */
public class InboundConnectionManager implements ConnectionManager {
	private static final Logger LOG = LogManager.getLogger();
	/** the current connection in use, initially not connected. there is no synchronization on this variable */
	private SyncConnection currentConn = NotConnectedConnection.getSingleton();
	/** locks the connection managed by this instance */
	private final ResourceLock<SyncConnection> lock = new ResourceLock<>(new ReentrantLock(), currentConn);
	/** condition to wait on for a new connection */
	private final Condition newConnection = lock.newCondition();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SyncConnection waitForConnection() throws InterruptedException {
		if (!currentConn.connected()) {
			currentConn = waitForNewConnection();
		}
		return currentConn;
	}

	/**
	 * Returns the current connection, does not wait for a new one if it's broken.
	 *
	 * This method is not thread safe
	 *
	 * @return the current connection
	 */
	@Override
	public SyncConnection getConnection() {
		return currentConn;
	}

	/**
	 * Waits for a new connection until it's supplied via the {@link #newConnection(SyncConnection)} method
	 *
	 * @return the new connection
	 * @throws InterruptedException
	 * 		if interrupted while waiting
	 */
	private SyncConnection waitForNewConnection() throws InterruptedException {
		try (final LockedResource<SyncConnection> lockedConn = lock.lock()) {
			while (!lockedConn.getResource().connected()) {
				newConnection.await();
			}
			return lockedConn.getResource();
		}
	}

	/**
	 * Provides a new connection initiated by the peer. If a peer establishes a new connection, we assume the previous
	 * one is broken, so we close and discard it.
	 *
	 * Note: The thread using this manager will not accept a new connection while it still has a valid one. This is
	 * another reason why this method has to close any connection that is currently open.
	 *
	 * @param connection
	 * 		a new connection
	 * @throws InterruptedException
	 * 		if the thread is interrupted while acquiring the lock
	 */
	public void newConnection(final SyncConnection connection) throws InterruptedException {
		try (final LockedResource<SyncConnection> lockedConn = lock.lockInterruptibly()) {
			final SyncConnection old = lockedConn.getResource();
			if (old.connected()) {
				LOG.error(SOCKET_EXCEPTIONS.getMarker(),
						"{} got new connection from {}, disconnecting old one",
						old.getSelfId(), old.getOtherId());
			}
			old.disconnect();
			lockedConn.setResource(connection);
			newConnection.signalAll();
		}
	}
}

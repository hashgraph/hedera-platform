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

import com.swirlds.platform.SyncConnection;

/**
 * Manages a single topological connection, not a single {@link SyncConnection}. This means that if the network topology
 * states that there should be a connection A->B, there will always be a single {@link ConnectionManager}. {@link
 * SyncConnection}s could break and be re-established, but they will always go though this single point.
 */
public interface ConnectionManager {
	/**
	 * Wait indefinitely until a connection is available. If there already is a connection, it will return immediately.
	 * If there is no connection, or it is broken, wait until it becomes available.
	 *
	 * @return the connection managed by this instance
	 * @throws InterruptedException
	 * 		if the thread gets interrupted while waiting
	 */
	SyncConnection waitForConnection() throws InterruptedException;

	/**
	 * Returns whatever connection is currently available, even if it's broken. This method should never block.
	 *
	 * @return the connection managed by this instance. can be broken but should never be null
	 */
	SyncConnection getConnection();

	/**
	 * Provides a new connection to this instance initiated by the peer
	 *
	 * @param connection
	 * 		the new connection established
	 * @throws InterruptedException
	 * 		thrown if the thread is interrupted while handing over the new connection
	 */
	void newConnection(final SyncConnection connection) throws InterruptedException;
}

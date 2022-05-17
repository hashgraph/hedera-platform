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

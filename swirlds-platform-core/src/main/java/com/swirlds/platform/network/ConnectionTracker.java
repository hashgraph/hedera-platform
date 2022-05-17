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
 * Tracks all connections that have been opened and closed by the platform
 */
public interface ConnectionTracker {
	/**
	 * Notifies the tracker that a new connection has been opened
	 *
	 * @param connection the connection that was just established
	 */
	void newConnectionOpened(final SyncConnection connection);

	/**
	 * Notifies the tracker that a connection has been closed
	 *
	 * @param outbound
	 * 		true if it was an outbound connection (initiated by self)
	 */
	void connectionClosed(final boolean outbound);
}

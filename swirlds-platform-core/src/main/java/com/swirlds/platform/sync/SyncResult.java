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

package com.swirlds.platform.sync;

import com.swirlds.common.NodeId;

/**
 * Information about a successful sync that just occurred
 */
public class SyncResult {
	private final boolean caller;
	private final NodeId otherId;
	private final int eventsRead;
	private final int eventsWritten;

	/**
	 * @param caller
	 * 		true if this node initiated the sync, false otherwise
	 * @param otherId
	 * 		the ID of the node we synced with
	 * @param eventsRead
	 * 		the number of events read during the sync
	 * @param eventsWritten
	 * 		the number of events written during the sync
	 */
	public SyncResult(
			final boolean caller,
			final NodeId otherId,
			final int eventsRead,
			final int eventsWritten) {
		this.caller = caller;
		this.otherId = otherId;
		this.eventsRead = eventsRead;
		this.eventsWritten = eventsWritten;
	}

	/**
	 * @return true if this node initiated the sync, false otherwise
	 */
	public boolean isCaller() {
		return caller;
	}

	/**
	 * @return the ID of the node we synced with
	 */
	public NodeId getOtherId() {
		return otherId;
	}

	/**
	 * @return the number of events read during the sync
	 */
	public int getEventsRead() {
		return eventsRead;
	}

	/**
	 * @return the number of events written during the sync
	 */
	public int getEventsWritten() {
		return eventsWritten;
	}
}

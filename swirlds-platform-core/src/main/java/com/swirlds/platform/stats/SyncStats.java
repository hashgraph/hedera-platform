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

package com.swirlds.platform.stats;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncTiming;

/**
 * Interface to update relevant sync statistics
 */
public interface SyncStats {
	/**
	 * Supplies the generation numbers of a sync for statistics
	 *
	 * @param self
	 * 		generations of our graph at the start of the sync
	 * @param other
	 * 		generations of their graph at the start of the sync
	 */
	void generations(final GraphGenerations self, final GraphGenerations other);

	/**
	 * Supplies information about the rate of receiving events when all events are read
	 *
	 * @param nanosStart
	 * 		The {@link System#nanoTime()} when we started receiving events
	 * @param numberReceived
	 * 		the number of events received
	 */
	void eventsReceived(final long nanosStart, final int numberReceived);

	/**
	 * @param bytesWritten
	 * 		the number of throttle bytes written during a sync
	 */
	void syncThrottleBytesWritten(final int bytesWritten);

	/**
	 * Record all stats related to sync timing
	 *
	 * @param syncTiming
	 * 		object that holds the timing data
	 * @param conn
	 * 		the sync connections
	 */
	void recordSyncTiming(final SyncTiming syncTiming, final SyncConnection conn);

	/**
	 * Records the size of the known set during a sync. This is the most compute intensive part of the sync, so this is
	 * useful information to validate sync performance.
	 *
	 * @param knownSetSize the size of the known set
	 */
	void knownSetSize(int knownSetSize);

	/**
	 * Notifies the stats that a sync is done
	 *
	 * @param info
	 * 		information about the sync that occurred
	 */
	void syncDone(final SyncResult info);

	/**
	 * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of creators
	 * that have more than one {@code sendTip} in the current synchronization.
	 *
	 * @param multiTipCount
	 * 		the number of creators in the current synchronization that have more than one sending tip.
	 */
	void updateMultiTipsPerSync(final int multiTipCount);

	/**
	 * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of {@code
	 * sendTips} in the current synchronization.
	 *
	 * @param tipCount
	 * 		the number of sending tips in the current synchronization.
	 */
	void updateTipsPerSync(final int tipCount);

	/**
	 * Called by {@link ShadowGraph} to update the number of generations that should
	 * be expired but can't be yet due to reservations.
	 *
	 * @param numGenerations
	 * 		the new number of generations
	 */
	void updateGensWaitingForExpiry(final long numGenerations);

	/**
	 * Called by {@link SyncManager#shouldAcceptSync()} when a sync is accepted or rejected to maintain the ratio of
	 * rejected syncs to accepted syncs.
	 *
	 * @param syncRejected
	 * 		true is a sync was rejected, false otherwise
	 */
	void updateRejectedSyncRatio(final boolean syncRejected);
}

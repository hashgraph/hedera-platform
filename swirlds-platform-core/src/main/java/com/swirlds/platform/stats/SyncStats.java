/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform.stats;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.sync.SyncTiming;

/**
 * Interface to update relevant sync statistics
 */
public interface SyncStats {
	/**
	 * @param eventsWritten
	 * 		the number of events written in a sync
	 */
	void syncEventsWritten(int eventsWritten);

	/**
	 * @param eventsRead
	 * 		the number of events read in a sync
	 */
	void syncEventsRead(int eventsRead);

	/**
	 * @param bytesWritten
	 * 		the number of throttle bytes written during a sync
	 */
	void syncThrottleBytesWritten(int bytesWritten);

	/**
	 * Record all stats related to sync timing
	 *
	 * @param syncTiming
	 * 		object that holds the timing data
	 * @param conn
	 * 		the sync connections
	 */
	void recordSyncTiming(SyncTiming syncTiming, SyncConnection conn);

	/**
	 * Notifies the stats that a sync is done
	 *
	 * @param caller
	 * 		was the platform a caller in this sync?
	 */
	void syncDone(boolean caller);

	/**
	 * Notifies the stats that the event creation phase has entered
	 *
	 * @param shouldCreateEvent
	 * 		did the sync manager tell us to create an event?
	 */
	void eventCreation(boolean shouldCreateEvent);

	/**
	 * Notifies the stats that a sync failed because one of the nodes is behind
	 */
	void nodeFallenBehind();

	/**
	 * Called by {@code NodeSynchronizerImpl} to update the {@code tips/sync} statistic with the number of creators that
	 * have more than one {@code sendTip} in the current synchronization.
	 *
	 * @param multiTipCount
	 * 		the number of creators in the current synchronization that have more than one sending tip.
	 */
	void updateMultiTipsPerSync(final int multiTipCount);

	/**
	 * Called by {@code NodeSynchronizerImpl} to update the {@code tips/sync} statistic with the number of {@code
	 * sendTips} in the current synchronization.
	 *
	 * @param tipCount
	 * 		the number of sending tips in the current synchronization.
	 */
	void updateTipsPerSync(final int tipCount);

	/**
	 * Updates the {@code tipAbsorptionOps/sec} statistic by cycling the underlying {@link
	 * com.swirlds.platform.StatsSpeedometer} instance.
	 */
	void updateTipAbsorptionOpsPerSec();
}

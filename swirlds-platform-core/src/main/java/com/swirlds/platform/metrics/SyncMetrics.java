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

package com.swirlds.platform.metrics;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.PlatformStatNames;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.Connection;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageAndMaxTimeStat;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.AverageTimeStat;
import com.swirlds.platform.stats.MaxStat;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncTiming;

import java.time.temporal.ChronoUnit;

import static com.swirlds.common.metrics.FloatFormats.*;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

/**
 * Interface to update relevant sync statistics
 */
public class SyncMetrics {

	private static final SpeedometerMetric.Config BYTES_PER_SECOND_CATCHUP_SENT_CONFIG =
			new SpeedometerMetric.Config(INTERNAL_CATEGORY, "bytes/sec_catchup")
					.withDescription("number of bytes sent per second to help others catch up")
					.withFormat(FORMAT_16_2);
	private final SpeedometerMetric bytesPerSecondCatchupSent;

	private static final RunningAverageMetric.Config FRAC_SYNC_SLOWED_CONFIG =
			new RunningAverageMetric.Config(INTERNAL_CATEGORY, "fracSyncSlowed")
					.withDescription("fraction of syncs that are slowed to let others catch up")
					.withFormat(FORMAT_9_6);
	private final RunningAverageMetric fracSyncSlowed;

	private static final RunningAverageMetric.Config AVG_BYTES_PER_SEC_SYNC_CONFIG =
			new RunningAverageMetric.Config(PLATFORM_CATEGORY, "bytes/sec_sync")
					.withDescription("average number of bytes per second transfered during a sync")
					.withFormat(FORMAT_16_2);
	private final RunningAverageMetric avgBytesPerSecSync;

	private static final SpeedometerMetric.Config CALL_SYNCS_PER_SECOND_CONFIG =
			new SpeedometerMetric.Config(PLATFORM_CATEGORY, "sync/secC")
					.withDescription("(call syncs) syncs completed per second initiated by this member")
					.withFormat(FORMAT_14_7);
	private final SpeedometerMetric callSyncsPerSecond;

	private static final SpeedometerMetric.Config REC_SYNCS_PER_SECOND_CONFIG =
			new SpeedometerMetric.Config(PLATFORM_CATEGORY, "sync/secR")
					.withDescription("(receive syncs) syncs completed per second initiated by other member")
					.withFormat(FORMAT_14_7);
	private final SpeedometerMetric recSyncsPerSecond;

	private static final RunningAverageMetric.Config TIPS_PER_SYNC_CONFIG =
			new RunningAverageMetric.Config(INTERNAL_CATEGORY, PlatformStatNames.TIPS_PER_SYNC)
					.withDescription("the average number of tips per sync at the start of each sync")
					.withFormat(FORMAT_15_3);
	private final RunningAverageMetric tipsPerSync;

	private final AverageStat syncGenerationDiff;
	private final AverageStat eventRecRate;
	private final AverageTimeStat avgSyncDuration1;
	private final AverageTimeStat avgSyncDuration2;
	private final AverageTimeStat avgSyncDuration3;
	private final AverageTimeStat avgSyncDuration4;
	private final AverageTimeStat avgSyncDuration5;
	private final AverageAndMaxTimeStat avgSyncDuration;
	private final AverageStat knownSetSize;
	private final AverageAndMax avgEventsPerSyncSent;
	private final AverageAndMax avgEventsPerSyncRec;
	private final MaxStat multiTipsPerSync;
	private final AverageStat gensWaitingForExpiry;
	private final AverageStat rejectedSyncRatio;


	/**
	 * Constructor of {@code SyncMetrics}
	 *
	 * @param metrics
	 * 		a reference to the metrics-system
	 * @throws IllegalArgumentException
	 * 		if {@code metrics} is {@code null}
	 */
	public SyncMetrics(final Metrics metrics) {
		bytesPerSecondCatchupSent = metrics.getOrCreate(BYTES_PER_SECOND_CATCHUP_SENT_CONFIG);
		fracSyncSlowed = metrics.getOrCreate(FRAC_SYNC_SLOWED_CONFIG);
		avgBytesPerSecSync = metrics.getOrCreate(AVG_BYTES_PER_SEC_SYNC_CONFIG);
		callSyncsPerSecond = metrics.getOrCreate(CALL_SYNCS_PER_SECOND_CONFIG);
		recSyncsPerSecond = metrics.getOrCreate(REC_SYNCS_PER_SECOND_CONFIG);
		tipsPerSync = metrics.getOrCreate(TIPS_PER_SYNC_CONFIG);

		avgSyncDuration = new AverageAndMaxTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync",
				"duration of average successful sync (in seconds)"
		);

		avgEventsPerSyncSent = new AverageAndMax(
				metrics,
				PLATFORM_CATEGORY,
				"ev/syncS",
				"number of events sent per successful sync",
				FORMAT_8_1
		);
		avgEventsPerSyncRec = new AverageAndMax(
				metrics,
				PLATFORM_CATEGORY,
				"ev/syncR",
				"number of events received per successful sync",
				FORMAT_8_1
		);

		syncGenerationDiff = new AverageStat(
				metrics,
				INTERNAL_CATEGORY,
				"syncGenDiff",
				"number of generation ahead (positive) or behind (negative) when syncing",
				FORMAT_8_1,
				AverageStat.WEIGHT_VOLATILE
		);
		eventRecRate = new AverageStat(
				metrics,
				INTERNAL_CATEGORY,
				"eventRecRate",
				"the rate at which we receive and enqueue events in ev/sec",
				FORMAT_8_1,
				AverageStat.WEIGHT_VOLATILE
		);

		avgSyncDuration1 = new AverageTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync1",
				"duration of step 1 of average successful sync (in seconds)"
		);
		avgSyncDuration2 = new AverageTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync2",
				"duration of step 2 of average successful sync (in seconds)"
		);
		avgSyncDuration3 = new AverageTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync3",
				"duration of step 3 of average successful sync (in seconds)"
		);
		avgSyncDuration4 = new AverageTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync4",
				"duration of step 4 of average successful sync (in seconds)"
		);
		avgSyncDuration5 = new AverageTimeStat(
				metrics,
				ChronoUnit.SECONDS,
				INTERNAL_CATEGORY,
				"sec/sync5",
				"duration of step 5 of average successful sync (in seconds)"
		);

		knownSetSize = new AverageStat(
				metrics,
				PLATFORM_CATEGORY,
				"knownSetSize",
				"the average size of the known set during a sync",
				FORMAT_10_3,
				AverageStat.WEIGHT_VOLATILE
		);

		multiTipsPerSync = new MaxStat(
				metrics,
				PLATFORM_CATEGORY,
				PlatformStatNames.MULTI_TIPS_PER_SYNC,
				"the number of creators that have more than one tip at the start of each sync",
				"%5d"
		);
		gensWaitingForExpiry = new AverageStat(
				metrics,
				PLATFORM_CATEGORY,
				PlatformStatNames.GENS_WAITING_FOR_EXPIRY,
				"the average number of generations waiting to be expired",
				FORMAT_5_3,
				AverageStat.WEIGHT_VOLATILE
		);
		rejectedSyncRatio = new AverageStat(
				metrics,
				INTERNAL_CATEGORY,
				PlatformStatNames.REJECTED_SYNC_RATIO,
				"the averaged ratio of rejected syncs to accepted syncs over time",
				FORMAT_1_3,
				AverageStat.WEIGHT_VOLATILE
		);
	}

	/**
	 * Supplies the generation numbers of a sync for statistics
	 *
	 * @param self
	 * 		generations of our graph at the start of the sync
	 * @param other
	 * 		generations of their graph at the start of the sync
	 */
	public void generations(final GraphGenerations self, final GraphGenerations other) {
		syncGenerationDiff.update(self.getMaxRoundGeneration() - other.getMaxRoundGeneration());
	}

	/**
	 * Supplies information about the rate of receiving events when all events are read
	 *
	 * @param nanosStart
	 * 		The {@link System#nanoTime()} when we started receiving events
	 * @param numberReceived
	 * 		the number of events received
	 */
	public void eventsReceived(final long nanosStart, final int numberReceived) {
		if (numberReceived == 0) {
			return;
		}
		final double nanos = ((double) System.nanoTime()) - nanosStart;
		final double seconds = nanos / ChronoUnit.SECONDS.getDuration().toNanos();
		eventRecRate.update(Math.round(numberReceived / seconds));
	}

	/**
	 * @param bytesWritten
	 * 		the number of throttle bytes written during a sync
	 */
	public void syncThrottleBytesWritten(final int bytesWritten) {
		bytesPerSecondCatchupSent.update(bytesWritten);
		fracSyncSlowed.update(bytesWritten > 0 ? 1 : 0);
	}

	/**
	 * Record all stats related to sync timing
	 *
	 * @param timing
	 * 		object that holds the timing data
	 * @param conn
	 * 		the sync connections
	 */
	public void recordSyncTiming(final SyncTiming timing, final Connection conn) {
		avgSyncDuration1.update(timing.getTimePoint(0), timing.getTimePoint(1));
		avgSyncDuration2.update(timing.getTimePoint(1), timing.getTimePoint(2));
		avgSyncDuration3.update(timing.getTimePoint(2), timing.getTimePoint(3));
		avgSyncDuration4.update(timing.getTimePoint(3), timing.getTimePoint(4));
		avgSyncDuration5.update(timing.getTimePoint(4), timing.getTimePoint(5));

		avgSyncDuration.update(timing.getTimePoint(0), timing.getTimePoint(5));
		final double syncDurationSec = timing.getPointDiff(5, 0) * Units.NANOSECONDS_TO_SECONDS;
		final double speed =
				Math.max(conn.getDis().getSyncByteCounter().getCount(), conn.getDos().getSyncByteCounter().getCount())
						/ syncDurationSec;

		// set the bytes/sec speed of the sync currently measured
		avgBytesPerSecSync.update(speed);
	}

	/**
	 * Records the size of the known set during a sync. This is the most compute intensive part of the sync, so this is
	 * useful information to validate sync performance.
	 *
	 * @param knownSetSize
	 * 		the size of the known set
	 */
	public void knownSetSize(final int knownSetSize) {
		this.knownSetSize.update(knownSetSize);
	}

	/**
	 * Notifies the stats that a sync is done
	 *
	 * @param info
	 * 		information about the sync that occurred
	 */
	public void syncDone(final SyncResult info) {
		if (info.isCaller()) {
			callSyncsPerSecond.cycle();
		} else {
			recSyncsPerSecond.cycle();
		}
		avgEventsPerSyncSent.update(info.getEventsWritten());
		avgEventsPerSyncRec.update(info.getEventsRead());
	}

	/**
	 * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of creators
	 * that have more than one {@code sendTip} in the current synchronization.
	 *
	 * @param multiTipCount
	 * 		the number of creators in the current synchronization that have more than one sending tip.
	 */
	public void updateMultiTipsPerSync(final int multiTipCount) {
		multiTipsPerSync.update(multiTipCount);
	}

	/**
	 * Called by {@link ShadowGraphSynchronizer} to update the {@code tips/sync} statistic with the number of {@code
	 * sendTips} in the current synchronization.
	 *
	 * @param tipCount
	 * 		the number of sending tips in the current synchronization.
	 */
	public void updateTipsPerSync(final int tipCount) {
		tipsPerSync.update(tipCount);
	}

	/**
	 * Called by {@link ShadowGraph} to update the number of generations that should
	 * be expired but can't be yet due to reservations.
	 *
	 * @param numGenerations
	 * 		the new number of generations
	 */
	public void updateGensWaitingForExpiry(final long numGenerations) {
		gensWaitingForExpiry.update(numGenerations);
	}

	/**
	 * Called by {@link SyncManager#shouldAcceptSync()} when a sync is accepted or rejected to maintain the ratio of
	 * rejected syncs to accepted syncs.
	 *
	 * @param syncRejected
	 * 		true is a sync was rejected, false otherwise
	 */
	public void updateRejectedSyncRatio(final boolean syncRejected) {
		rejectedSyncRatio.update(syncRejected);
	}
}

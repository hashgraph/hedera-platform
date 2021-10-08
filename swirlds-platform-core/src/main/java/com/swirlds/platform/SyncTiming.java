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

package com.swirlds.platform;

import com.swirlds.common.Units;

/**
 * A type to record the points for gossip steps. At the end of a gossip session,
 * the results are reported to the relevant statistics accumulators which quantify
 * gossip performance.
 */
class SyncTiming {
	/**
	 * number of time points to record = {number of time intervals} + 1
	 */
	private static final int NUM_TIME_POINTS = 6;

	/**
	 * JVM time points, in nanoseconds
	 */
	private final long[] t = new long[NUM_TIME_POINTS];

	/**
	 * Empty default ctor
	 */
	protected SyncTiming() {

	}

	/**
	 * Set the 0th time point
	 */
	protected void start() {
		t[0] = now();
	}

	/**
	 * Record the ith time point
	 *
	 * @param i
	 * 		the ith time point to record
	 */
	protected void setTimePoint(final int i) {
		t[i] = now();
	}

	/**
	 * Get the ith time point
	 *
	 * @param i
	 * 		the index of the time point
	 * @return the ith recorded time point
	 */
	protected long getTimePoint(final int i) {
		return t[i];
	}

	/**
	 * Get the current system time value in ns
	 *
	 * @return current time in ns
	 */
	private static long now() {
		return System.nanoTime();
	}

	/**
	 * Update statistics for a single sync operation at completion.
	 *
	 * @param conn
	 * 		The connection instance used for the sync.
	 * @param stats
	 * 		The statistics accumulator to be updated.
	 */
	protected void finish(final SyncConnection conn, final Statistics stats) {
		stats.avgSyncDuration1.recordValue((t[1] - t[0]) * Units.NANOSECONDS_TO_SECONDS);
		stats.avgSyncDuration2.recordValue((t[2] - t[1]) * Units.NANOSECONDS_TO_SECONDS);
		stats.avgSyncDuration3.recordValue((t[3] - t[2]) * Units.NANOSECONDS_TO_SECONDS);
		stats.avgSyncDuration4.recordValue((t[4] - t[3]) * Units.NANOSECONDS_TO_SECONDS);

		// SyncConnection.disconnect sets the stream references to null, so if the connection has been
		// closed before this line executes, we can not get the byte counts. This is (part of) the subject of
		// issue #3197.
		if(!conn.connected()) {
			return;
		}

		final double syncDurationSec = (t[5] - t[0]) * Units.NANOSECONDS_TO_SECONDS;
		stats.avgSyncDuration.recordValue(syncDurationSec);
		stats.maxSyncTimeSec.accumulate(syncDurationSec);
		final double speed =
				Math.max(conn.getDis().getSyncByteCounter().getCount(), conn.getDos().getSyncByteCounter().getCount())
						/ syncDurationSec;

		// set the bytes/sec speed of the sync currently measured
		conn.getPlatform().setLastSyncSpeed(conn.getOtherId().getIdAsInt(), speed);
		stats.avgBytesPerSecSync.recordValue(speed);
	}

}

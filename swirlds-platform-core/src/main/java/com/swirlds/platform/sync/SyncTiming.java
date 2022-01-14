/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.sync;

/**
 * A type to record the points for gossip steps. At the end of a gossip session,
 * the results are reported to the relevant statistics accumulators which quantify
 * gossip performance.
 */
public class SyncTiming {
	/**
	 * number of time points to record = {number of time intervals} + 1
	 */
	private static final int NUM_TIME_POINTS = 6;

	/**
	 * JVM time points, in nanoseconds
	 */
	private final long[] t = new long[NUM_TIME_POINTS];

	/**
	 * Set the 0th time point
	 */
	public void start() {
		t[0] = now();
	}

	/**
	 * Record the ith time point
	 *
	 * @param i
	 * 		the ith time point to record
	 */
	public void setTimePoint(final int i) {
		t[i] = now();
	}

	/**
	 * Get the ith time point
	 *
	 * @param i
	 * 		the index of the time point
	 * @return the ith recorded time point
	 */
	public long getTimePoint(final int i) {
		return t[i];
	}

	/**
	 * Get the difference between two time points
	 *
	 * @param end
	 * 		the ending point
	 * @param start
	 * 		the starting point
	 * @return the difference
	 */
	public long getPointDiff(final int end, final int start) {
		return t[end] - t[start];
	}

	/**
	 * Get the current system time value in ns
	 *
	 * @return current time in ns
	 */
	private static long now() {
		return System.nanoTime();
	}
}

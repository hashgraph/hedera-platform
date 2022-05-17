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

package com.swirlds.platform.stats;

import com.swirlds.common.statistics.StatEntry;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A stat designed to track the amount of time spent in various parts of a cycle that is repeated over and over.
 */
public class CycleTimingStat {

	/** The average amount of time spent on a single cycle. */
	private final TimeStat totalCycleTimeStat;

	/** The average amount of time spent on each interval in the cycle. */
	private final List<TimeStat> timePointStats;

	/** The number of intervals in this cycle. */
	private final int numIntervals;

	/** JVM time points that define the beginning and end of each interval, in nanoseconds */
	private final long[] t;

	/**
	 * <p>Creates a new instance whose number of time points is equal to the number of time intervals to be recorded.
	 * For example, a cycle that requires 3 intervals to be recorded (interval 1, interval 2, and interval 3) should be
	 * initialized with {@code numIntervals} equals to 3. Each part of the cycle is measured using the called shown
	 * below:</p>
	 *
	 * <p>Interval 1: from {@code startCycle()} to {@code timePoint(1)}</p>
	 * <p>Interval 2: from {@code timePoint(1)} to {@code timePoint(2)}</p>
	 * <p>Interval 3: from {@code timePoint(2)} to {@code stopCycle()}</p>
	 *
	 * @param unit
	 * 		the unit of time to measure all time intervals in this cycle
	 * @param category
	 * 		the category of this stat
	 * @param name
	 * 		a name that describes the cycle in its entirety
	 * @param numIntervals
	 * 		the number of intervals this cycle will record
	 * @param detailedNames
	 * 		a list of names and describe each time interval. The size must match {@code numIntervals}
	 * @param descList
	 * 		a list of descriptions for each time interval. The size must match {@code numIntervals}
	 */
	public CycleTimingStat(
			final ChronoUnit unit,
			final String category,
			final String name,
			final int numIntervals,
			final List<String> detailedNames,
			final List<String> descList) {

		if (numIntervals < 1) {
			throw new IllegalArgumentException(("The number of intervals must be at least 1."));
		}
		if (descList.size() != numIntervals) {
			throw new IllegalArgumentException(
					String.format("The number of descriptions for %s (%s) does not match the number of intervals " +
							"(%s).", name, descList.size(), numIntervals));
		}
		if (detailedNames.size() != numIntervals) {
			throw new IllegalArgumentException(
					String.format("The number of detailed named for %s (%s) does not match the number of intervals " +
							"(%s).", name, detailedNames.size(), numIntervals));
		}

		this.numIntervals = numIntervals;
		t = new long[numIntervals + 1];

		timePointStats = new ArrayList<>(numIntervals);
		for (int i = 0; i < numIntervals; i++) {
			timePointStats.add(new TimeStat(
					unit,
					category,
					name + "-" + detailedNames.get(i),
					descList.get(i),
					AverageStat.WEIGHT_VOLATILE
			));
		}
		totalCycleTimeStat = new TimeStat(unit,
				category,
				name + "-total",
				"average total time spend in the " + name + " cycle.",
				AverageStat.WEIGHT_VOLATILE);
	}

	/**
	 * Mark the end of the current interval as the time of invocation and begin the next interval.
	 *
	 * @param i
	 * 		the ith time point to record
	 */
	public void setTimePoint(final int i) {
		if (i <= 0) {
			throw new IllegalArgumentException(
					"Time point must be greater than 0. Use startCycle() to mark the beginning of the cycle.");
		} else if (i >= numIntervals) {
			throw new IllegalArgumentException(
					String.format("Time point must be less than %s. Use stopCycle() to mark the end of the cycle.",
							numIntervals - 1));
		} else {
			t[i] = now();
		}
	}

	public void startCycle() {
		t[0] = now();
	}

	/**
	 * Capture the end of the cycle time and update all stats for the time periods during the cycle.
	 */
	public void stopCycle() {
		t[numIntervals] = now();

		for (int i = 0; i < t.length - 1; i++) {
			timePointStats.get(i).update(t[i], t[i + 1]);
		}

		totalCycleTimeStat.update(t[0], t[t.length - 1]);
	}

	public List<StatEntry> getAllEntries() {
		final List<StatEntry> statEntries =
				timePointStats.stream().map(TimeStat::getAverageStat).collect(Collectors.toList());
		statEntries.add(totalCycleTimeStat.getAverageStat());
		return statEntries;
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

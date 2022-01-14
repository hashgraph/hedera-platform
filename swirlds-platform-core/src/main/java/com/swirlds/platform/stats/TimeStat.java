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

package com.swirlds.platform.stats;

import com.swirlds.common.StatEntry;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class TimeStat {
	private static final String FORMAT_DEFAULT = "%,10.3f";
	private static final String FORMAT_SECONDS = "%,5.4f";
	private static final String FORMAT_MILLIS = "%,10.1f";

	private final ChronoUnit unit;
	private final AtomicAverage average;
	private final StatEntry avgEntry;
	private final AtomicMax max;
	private final StatEntry maxEntry;

	public TimeStat(
			final ChronoUnit unit,
			final String category,
			final String name,
			final String desc) {
		this.unit = unit;
		average = new AtomicAverage(AverageStat.WEIGHT_SMOOTH);
		max = new AtomicMax();

		final String format;
		switch (unit) {
			case MILLIS:
				format = FORMAT_MILLIS;
				break;
			case SECONDS:
				format = FORMAT_SECONDS;
				break;
			default:
				format = FORMAT_DEFAULT;
		}
		avgEntry = new StatEntry(
				category,
				name,
				desc,
				format,
				null,
				null,
				this::resetAvg,
				this::getAvg);
		maxEntry = new StatEntry(
				category,
				name + "MAX",
				"max value of " + name,
				format,
				null,
				null,
				this::resetMax,
				this::getMax,
				this::getAndResetMax);// the max we reset after each write to the CSV
	}

	private double convert(final long nanos) {
		return convert((double) nanos);
	}

	private double convert(final double nanos) {
		return nanos / unit.getDuration().toNanos();
	}

	private double getAvg() {
		return convert(average.get());
	}

	private void resetAvg(final double unused) {
		average.reset();
	}

	private double getMax() {
		return convert(max.get());
	}

	private void resetMax(final double unused) {
		max.reset();
	}

	private double getAndResetMax() {
		return convert(max.getAndReset());
	}

	public void update(final long startTime) {
		update(startTime, System.nanoTime());
	}

	public void update(final long start, final long end) {
		// the value is stored as nanos and converted upon retrieval
		final long nanos = end - start;
		average.update(nanos);
		max.update(nanos);
	}

	public StatEntry getAverageStat() {
		return avgEntry;
	}


	public StatEntry getMaxStat() {
		return maxEntry;
	}

	public List<StatEntry> getAllEntries() {
		return List.of(avgEntry, maxEntry);
	}
}

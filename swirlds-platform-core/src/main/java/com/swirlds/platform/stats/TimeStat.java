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

import com.swirlds.common.statistics.StatEntry;

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
		this(unit, category, name, desc, AverageStat.WEIGHT_SMOOTH);
	}

	public TimeStat(
			final ChronoUnit unit,
			final String category,
			final String name,
			final String desc,
			final double weight) {
		this.unit = unit;
		average = new AtomicAverage(weight);
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

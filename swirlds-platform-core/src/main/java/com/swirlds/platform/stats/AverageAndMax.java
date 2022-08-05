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

import java.util.List;

/**
 * A statistic object to track an average number, without history. This class uses an {@link AtomicAverage} so it is both
 * thread safe and performant.
 */
public class AverageAndMax {
	private static final String FORMAT_MAX = "%10d";
	private final AverageStat averageStat;
	private final MaxStat maxStat;

	/**
	 * @param category
	 * 		the kind of statistic (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param desc
	 * 		a one-sentence description of the statistic
	 * @param averageFormat
	 * 		a string that can be passed to String.format() to format the statistic for the average number
	 */
	public AverageAndMax(
			final String category,
			final String name,
			final String desc,
			final String averageFormat) {
		this(category, name, desc, averageFormat, AverageStat.WEIGHT_SMOOTH);
	}

	/**
	 * @param category
	 * 		the kind of statistic (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param desc
	 * 		a one-sentence description of the statistic
	 * @param averageFormat
	 * 		a string that can be passed to String.format() to format the statistic for the average number
	 * @param weight
	 * 		the weight used to calculate the average
	 */
	public AverageAndMax(
			final String category,
			final String name,
			final String desc,
			final String averageFormat,
			final double weight) {
		averageStat = new AverageStat(
				category,
				name,
				desc,
				averageFormat,
				weight);
		maxStat = new MaxStat(
				category,
				name + "MAX",
				"max value of " + name,
				FORMAT_MAX);
	}


	public void update(final long value) {
		averageStat.update(value);
		maxStat.update(value);
	}


	public StatEntry getAverageStat() {
		return averageStat.getStatEntry();
	}


	public StatEntry getMaxStat() {
		return maxStat.getStatEntry();
	}

	public List<StatEntry> getAllEntries() {
		return List.of(averageStat.getStatEntry(), maxStat.getStatEntry());
	}
}

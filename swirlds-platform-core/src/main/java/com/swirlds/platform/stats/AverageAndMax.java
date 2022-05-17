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

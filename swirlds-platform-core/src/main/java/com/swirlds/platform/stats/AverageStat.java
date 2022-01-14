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

public class AverageStat {
	/** does not change very quickly */
	public static final double WEIGHT_SMOOTH = 0.01;
	/** changes average quite rapidly */
	public static final double WEIGHT_VOLATILE = 0.1;

	private final AtomicAverage average;
	private final StatEntry statEntry;

	/**
	 * @param category
	 * 		the kind of statistic (stats are grouped or filtered by this)
	 * @param name
	 * 		a short name for the statistic
	 * @param desc
	 * 		a one-sentence description of the statistic
	 * @param format
	 * 		a string that can be passed to String.format() to format the statistic for the average number
	 * @param weight
	 * 		the weight used to calculate the average
	 */
	public AverageStat(
			final String category,
			final String name,
			final String desc,
			final String format,
			final double weight) {
		average = new AtomicAverage(weight);
		statEntry = new StatEntry(
				category,
				name,
				desc,
				format,
				null,
				null,
				this::reset,
				average::get);
	}

	private void reset(final double unused) {
		average.reset();
	}

	/**
	 * Update the average value
	 *
	 * @param value
	 * 		the value to update with
	 */
	public void update(final long value) {
		average.update(value);
	}

	/**
	 * Updates the average with 1 or 0 depending on the boolean value
	 *
	 * @param value
	 * 		the value used to update the average
	 */
	public void update(final boolean value) {
		update(value ? 1 : 0);
	}

	/**
	 * @return a {@link StatEntry} of the average number to provide to {@link com.swirlds.common.Statistics}
	 */
	public StatEntry getStatEntry() {
		return statEntry;
	}
}

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

public class MaxStat {
	private final AtomicMax max;
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
	 */
	public MaxStat(
			final String category,
			final String name,
			final String desc,
			final String format) {
		max = new AtomicMax();
		statEntry = new StatEntry(
				category,
				name,
				desc,
				format,
				null,
				null,
				this::resetMax,
				max::get,
				max::getAndReset);// the max we reset after each write to the CSV
	}

	private void resetMax(final double unused) {
		max.reset();
	}

	/**
	 * Update the max value
	 *
	 * @param value
	 * 		the value to update with
	 */
	public void update(long value) {
		max.update(value);
	}

	/**
	 * @return a {@link StatEntry} of the max number to provide to {@link com.swirlds.common.Statistics}
	 */
	public StatEntry getStatEntry() {
		return statEntry;
	}
}

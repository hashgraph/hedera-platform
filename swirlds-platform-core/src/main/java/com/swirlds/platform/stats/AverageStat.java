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
	 * @return a {@link StatEntry} of the average number to provide to {@link com.swirlds.common.statistics.Statistics}
	 */
	public StatEntry getStatEntry() {
		return statEntry;
	}
}

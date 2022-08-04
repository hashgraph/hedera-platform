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
	 * @return a {@link StatEntry} of the max number to provide to {@link com.swirlds.common.statistics.Statistics}
	 */
	public StatEntry getStatEntry() {
		return statEntry;
	}
}

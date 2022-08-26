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

package com.swirlds.platform.stats.cycle;

import com.swirlds.common.utility.Units;
import com.swirlds.platform.stats.atomic.AtomicIntPair;

/**
 * Tracks the fraction of busy time to idle in a cycle
 */
public class CycleBusyTime extends PercentageMetric {
	private static final int PERCENT = 100;
	private final AtomicIntPair intPair = new AtomicIntPair();

	public CycleBusyTime(final String category, final String name, final String description) {
		super(category, name, description);
	}

	private static double busyPercentage(final int b, final int i) {
		final int t = b + i;
		if (t == 0) {
			return 0;
		}
		return (((double) b) / t) * PERCENT;
	}

	/**
	 * Add the amount of time the thread was busy
	 *
	 * @param nanoTime
	 * 		the number of nanoseconds a thread was busy
	 */
	public void addBusyTime(final long nanoTime) {
		intPair.accumulate((int) (nanoTime * Units.NANOSECONDS_TO_MICROSECONDS), 0);
	}

	/**
	 * Add the amount of time the thread was idle
	 *
	 * @param nanoTime
	 * 		the number of nanoseconds a thread was idle
	 */
	public void addIdleTime(final long nanoTime) {
		intPair.accumulate(0, (int) (nanoTime * Units.NANOSECONDS_TO_MICROSECONDS));
	}

	@Override
	public Object getValue() {
		return intPair.computeDouble(CycleBusyTime::busyPercentage);
	}

	@Override
	public Object getValueAndReset() {
		return intPair.computeDoubleAndReset(CycleBusyTime::busyPercentage);
	}
}

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

package com.swirlds.platform.stats.simple;

import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.stats.atomic.AtomicSumAndCount;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * Tracks the average time taken for an operation by accumulating the time and the number of operation. The actual
 * average is calculated when written to the output, thus providing the most accurate average for the write period with
 * minimal overhead. This class accumulates time in microseconds and stores it in an integer, which means the maximum
 * accumulated time is about 35 minutes before it overflows.
 */
public class AccumulatedAverageTime extends Metric {
	private static final String UNIT_APPENDIX = " (ms)";
	private final AtomicSumAndCount sumAndCount;

	public AccumulatedAverageTime(final String category, final String name, final String description) {
		super(category, name + UNIT_APPENDIX, description, FloatFormats.FORMAT_10_6);
		this.sumAndCount = new AtomicSumAndCount();
	}

	/**
	 * Add time to the accumulated value
	 *
	 * @param nanoTime
	 * 		the time in nanoseconds
	 */
	public void add(final long nanoTime) {
		sumAndCount.add((int) (nanoTime * Units.NANOSECONDS_TO_MICROSECONDS));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ValueType> getValueTypes() {
		return Metric.VALUE_TYPE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Double get(final ValueType valueType) {
		if (valueType == VALUE) {
			return sumAndCount.average() * Units.MICROSECONDS_TO_MILLISECONDS;
		}
		throw new IllegalArgumentException("Unknown MetricType");
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		return List.of(Pair.of(VALUE, sumAndCount.averageAndReset() * Units.MICROSECONDS_TO_MILLISECONDS));
	}
}

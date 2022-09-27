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

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.stats.atomic.AtomicIntPair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * Tracks the fraction of time spent in a single interval of a cycle
 */
public class IntervalPercentageMetric extends PercentageMetric {
	private final AtomicIntPair totalAndInterval;

	public IntervalPercentageMetric(final CycleDefinition definition, final int intervalIndex) {
		super(
				definition.getCategory(),
				definition.getName() + "-" + definition.getIntervalName(intervalIndex),
				definition.getIntervalDescription(intervalIndex));
		totalAndInterval = new AtomicIntPair();
	}

	/**
	 * Update the time taken for this interval
	 *
	 * @param cycleNanoTime
	 * 		the number of nanoseconds the whole cycle lasted
	 * @param intervalNanoTime
	 * 		the number of nanoseconds this interval lasted
	 */
	public void updateTime(final long cycleNanoTime, final long intervalNanoTime) {
		totalAndInterval.accumulate(toMicros(cycleNanoTime), toMicros(intervalNanoTime));
	}

	private int toMicros(final long nanos){
		return (int) (nanos * Units.NANOSECONDS_TO_MICROSECONDS);
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
			return totalAndInterval.computeDouble(PercentageMetric::calculatePercentage);
		}
		throw new IllegalArgumentException("Unknown MetricType");
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		return List.of(Pair.of(VALUE, totalAndInterval.computeDoubleAndReset(PercentageMetric::calculatePercentage)));
	}
}

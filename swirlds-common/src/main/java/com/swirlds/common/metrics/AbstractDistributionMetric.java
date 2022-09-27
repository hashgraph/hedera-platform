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

package com.swirlds.common.metrics;

import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.utility.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Represents a metric computed over a distribution function
 */
public abstract class AbstractDistributionMetric extends Metric {

	protected final double halfLife;

	/**
	 * Constructor of {@code AbstractDistributionMetric}
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @param halfLife
	 * 		the halfLife of the metric
	 * @throws IllegalArgumentException if one of the parameters is {@code null}
	 */
	protected AbstractDistributionMetric(
			final String category,
			final String name,
			final String description,
			final String format,
			final double halfLife
	) {
		super(category, name, description, format);
		this.halfLife = halfLife;
	}

	/**
	 * Returns the mean value of this {@code Metric}.
	 *
	 * @return the current value
	 */
	public abstract double get();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ValueType> getValueTypes() {
		return Metric.DISTRIBUTION_TYPES;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public Double get(final ValueType valueType) {
		CommonUtils.throwArgNull(valueType, "valueType");
		return switch (valueType) {
			case VALUE -> get();
			case MAX -> getStatsBuffered().getMax();
			case MIN -> getStatsBuffered().getMin();
			case STD_DEV -> getStatsBuffered().getStdDev();
			default -> throw new IllegalArgumentException("Unsupported ValueType");
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		final StatsBuffered statsBuffered = getStatsBuffered();
		return List.of(
				Pair.of(ValueType.VALUE, get()),
				Pair.of(ValueType.MAX, statsBuffered.getMax()),
				Pair.of(ValueType.MIN, statsBuffered.getMin()),
				Pair.of(ValueType.STD_DEV, statsBuffered.getStdDev())
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public void reset() {
		getStatsBuffered().reset(halfLife);
	}

}

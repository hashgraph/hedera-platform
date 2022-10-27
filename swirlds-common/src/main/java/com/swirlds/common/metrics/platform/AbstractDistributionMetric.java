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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotValue;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.utility.CommonUtils;

import java.util.List;

/**
 * Represents a metric computed over a distribution function
 */
public abstract class AbstractDistributionMetric extends PlatformMetric {

	protected final double halfLife;

	AbstractDistributionMetric(final MetricConfig<?, ?> config, final double halfLife) {
		super(config);
		this.halfLife = halfLife;
	}

	/**
	 * Returns the mean value of this {@code Metric}.
	 *
	 * @return the current value
	 */
	public abstract double get();

	/**
	 * Getter of the {@code halfLife}
	 *
	 * @return the {@code halfLife}
	 */
	public double getHalfLife() {
		return halfLife;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Double get(final ValueType valueType) {
		CommonUtils.throwArgNull(valueType, "valueType");
		return switch (valueType) {
			case VALUE -> get();
			case MAX -> getStatsBuffered().getMax();
			case MIN -> getStatsBuffered().getMin();
			case STD_DEV -> getStatsBuffered().getStdDev();
			default -> throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SnapshotValue> takeSnapshot() {
		final StatsBuffered statsBuffered = getStatsBuffered();
		return List.of(
				new SnapshotValue(ValueType.VALUE, get()),
				new SnapshotValue(ValueType.MAX, statsBuffered.getMax()),
				new SnapshotValue(ValueType.MIN, statsBuffered.getMin()),
				new SnapshotValue(ValueType.STD_DEV, statsBuffered.getStdDev())
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void reset() {
		getStatsBuffered().reset(halfLife);
	}

}

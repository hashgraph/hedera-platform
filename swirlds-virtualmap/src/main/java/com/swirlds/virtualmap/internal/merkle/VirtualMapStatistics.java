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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;

/**
 * Encapsulates statistics for a virtual map.
 */
public class VirtualMapStatistics {

	private static final String STAT_CATEGORY = "virtual-map";
	private static final double DEFAULT_HALF_LIFE = 5;

	private final List<Metric> statistics;

	private final LongGauge size;

	private final IntegerGauge flushBacklogSize;

	/**
	 * The average time to call the VirtualMap flush() method.
	 */
	private final RunningAverageMetric flushLatency;

	/**
	 * The average time to call the cache merge() method.
	 */
	private final RunningAverageMetric mergeLatency;

	/**
	 * Create a new statistics instance for a virtual map family.
	 *
	 * @param label
	 * 		the label for the virtual map
	 */
	public VirtualMapStatistics(final String label) {
		statistics = new LinkedList<>();

		size = new LongGauge(
				STAT_CATEGORY,
				"vMapSize_" + label,
				"The size of the VirtualMap '" + label + "'"
		);
		flushLatency = new RunningAverageMetric(
				STAT_CATEGORY,
				"vMapFlushLatency_" + label,
				"The flush latency of VirtualMap '" + label + "'",
				FORMAT_10_2,
				DEFAULT_HALF_LIFE
		);
		mergeLatency = new RunningAverageMetric(
				STAT_CATEGORY,
				"vMapMergeLatency_" + label,
				"The merge latency of VirtualMap '" + label + "'",
				FORMAT_10_2,
				DEFAULT_HALF_LIFE
		);
		flushBacklogSize = new IntegerGauge(
				STAT_CATEGORY,
				"vMapFlushBacklog_" + label,
				"the number of '" + label + "' copies waiting to be flushed"
		);

		statistics.addAll(List.of(
				size,
				flushLatency,
				mergeLatency,
				flushBacklogSize
		));
	}

	/**
	 * Register all statistics with a registry.
	 *
	 * @param registry
	 * 		an object that manages statistics
	 */
	public void registerStatistics(final Consumer<Metric> registry) {
		for (final Metric metric : statistics) {
			registry.accept(metric);
		}
	}

	/**
	 * Update the size statistic for the virtual map.
	 *
	 * @param size
	 * 		the current size
	 */
	public void setSize(final long size) {
		this.size.set(size);
	}

	/**
	 * Record the current flush latency for the virtual map.
	 *
	 * @param flushLatency
	 * 		the current flush latency
	 */
	public void recordFlushLatency(final double flushLatency) {
		this.flushLatency.recordValue(flushLatency);
	}

	/**
	 * Record the current merge latency for the virtual map.
	 *
	 * @param mergeLatency
	 * 		the current merge latency
	 */
	public void recordMergeLatency(final double mergeLatency) {
		this.mergeLatency.recordValue(mergeLatency);
	}

	/**
	 * Record the current number of virtual maps that are waiting to be flushed.
	 *
	 * @param flushBacklogSize
	 * 		the number of maps that need to be flushed but have not yet been flushed
	 */
	public void recordFlushBacklogSize(final int flushBacklogSize) {
		this.flushBacklogSize.set(flushBacklogSize);
	}
}

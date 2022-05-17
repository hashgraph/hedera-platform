/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.StatsRunningAverage;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates statistics for a virtual map.
 */
public class VirtualMapStatistics {

	private static final String STAT_CATEGORY = "virtual-map";
	private static final double DEFAULT_HALF_LIFE = 5;

	private static final String INT_FORMAT = "%d";
	private static final String FLOAT_FORMAT = "%,10.2f";

	private final List<StatEntry> statistics;

	private final AtomicLong size;

	private final AtomicInteger flushBacklogSize = new AtomicInteger();

	/**
	 * The average time to call the VirtualMap flush() method.
	 */
	private StatsRunningAverage flushLatency;

	/**
	 * The average time to call the cache merge() method.
	 */
	private StatsRunningAverage mergeLatency;

	/**
	 * Create a new statistics instance for a virtual map family.
	 *
	 * @param label
	 * 		the label for the virtual map
	 */
	public VirtualMapStatistics(final String label) {
		statistics = new LinkedList<>();

		size = new AtomicLong();
		flushLatency = new StatsRunningAverage(DEFAULT_HALF_LIFE);
		mergeLatency = new StatsRunningAverage(DEFAULT_HALF_LIFE);

		buildStatistics(label);
	}

	/**
	 * This method fills in some default arguments for the StatEntry constructor and adds the statistic to the
	 * list of all statistics.
	 */
	private void buildStatistic(
			final String name,
			final String description,
			final String format,
			final Supplier<Object> getStat) {

		buildStatistic(name, description, format, null, null, getStat);
	}

	/**
	 * This method fills in some default arguments for the StatEntry constructor and adds the statistic to the
	 * list of all statistics.
	 */
	private void buildStatistic(
			final String name,
			final String description,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Supplier<Object> getStat) {

		statistics.add(new StatEntry(STAT_CATEGORY, name, description, format, buffered, init, null, getStat));
	}

	private void buildStatistics(final String label) {
		buildStatistic("vMapSize_" + label,
				"The size of the VirtualMap '" + label + "'",
				INT_FORMAT,
				size::get);

		buildStatistic("vMapFlushLatency_" + label,
				"The flush latency of VirtualMap '" + label + "'",
				FLOAT_FORMAT,
				flushLatency,
				h -> {
					flushLatency = new StatsRunningAverage(h);
					return flushLatency;
				},
				flushLatency::getWeightedMean);

		buildStatistic("vMapMergeLatency_" + label,
				"The merge latency of VirtualMap '" + label + "'",
				FLOAT_FORMAT,
				mergeLatency,
				h -> {
					mergeLatency = new StatsRunningAverage(h);
					return mergeLatency;
				},
				mergeLatency::getWeightedMean);

		buildStatistic("vMapFlushBacklog_" + label,
				"the number of '" + label + "' copies waiting to be flushed",
				INT_FORMAT,
				flushBacklogSize::get);
	}

	/**
	 * Register all statistics with a registry.
	 *
	 * @param registry
	 * 		an object that manages statistics
	 */
	public void registerStatistics(final Consumer<StatEntry> registry) {
		for (final StatEntry statEntry : statistics) {
			registry.accept(statEntry);
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

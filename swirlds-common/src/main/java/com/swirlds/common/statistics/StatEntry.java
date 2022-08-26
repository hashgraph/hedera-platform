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

package com.swirlds.common.statistics;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

/**
 * A very flexible implementation of Metric which behavior is mostly passed to it via lambdas.
 */
public class StatEntry extends Metric {

	/** the statistics object (if it implements StatsBuffered), else null */
	private StatsBuffered buffered;
	/** a lambda that instantiates and initializes, using the given half life */
	private final Function<Double, StatsBuffered> init;
	/** a lambda that resets the statistic, using the given half life */
	private final Consumer<Double> reset;
	/** a lambda that returns the statistic string */
	private final Supplier<Object> statsStringSupplier;
	/** a lambda that returns the statistic string and resets it at the same time */
	private final Supplier<Object> resetStatsStringSupplier;

	/**
	 * stores all the parameters, which can be accessed directly
	 *
	 * @param category
	 * 		the kind of metric (metrics are grouped or filtered by this)
	 * @param name
	 * 		a short name for the metric
	 * @param description
	 * 		a one-sentence description of the metric
	 * @param format
	 * 		a string that can be passed to String.format() to format the metric
	 * @param buffered
	 * 		the metric object (if it implements StatsBuffered), else null
	 * @param init
	 * 		a lambda that instantiates and initializes, using the given half life
	 * @param reset
	 * 		a lambda that resets the metric, using the given half life
	 * @param statsStringSupplier
	 * 		a lambda that returns the metric string
	 * @param resetStatsStringSupplier
	 * 		a lambda that returns the metric string and resets the value at the same time
	 * @throws IllegalArgumentException if {@code category}, {@code name}, {@code description}, {@code format},
	 * {@code statsStringSupplier} or {@code resetStatsStringSupplier} is {@code null}
	 */
	public StatEntry(final String category,
			final String name,
			final String description,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Consumer<Double> reset,
			final Supplier<Object> statsStringSupplier,
			final Supplier<Object> resetStatsStringSupplier) {
		super(category, name, description, format);
		this.buffered = buffered;
		this.init = init;
		this.reset = reset;
		this.statsStringSupplier = throwArgNull(statsStringSupplier, "statsStringSupplier");
		this.resetStatsStringSupplier = throwArgNull(resetStatsStringSupplier, "resetStatsStringSupplier");
	}

	/**
	 * Same as
	 * {@link StatEntry#StatEntry(String, String, String, String, StatsBuffered, Function, Consumer, Supplier, Supplier)}
	 * with the resetSupplier being set to supplier
	 */
	public StatEntry(final String category,
			final String name,
			final String desc,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Consumer<Double> reset,
			final Supplier<Object> statsStringSupplier) {
		this(category, name, desc, format, buffered, init, reset, statsStringSupplier, statsStringSupplier);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public void init() {
		if (init != null) {
			buffered = init.apply(SettingsCommon.halfLife);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void reset() {
		if (reset != null) {
			reset.accept(SettingsCommon.halfLife);
		} else if (buffered != null) {
			buffered.reset(SettingsCommon.halfLife);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public Object getValue() {
		return statsStringSupplier.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public Object getValueAndReset() {
		return resetStatsStringSupplier.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public StatsBuffered getStatsBuffered() {
		return buffered;
	}
}

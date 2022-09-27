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
import com.swirlds.common.utility.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

/**
 * A very flexible implementation of Metric which behavior is mostly passed to it via lambdas.
 *
 * @deprecated This class should not be used in new code anymore. Instead of the specialized {@link Metric}
 * implementation should be used.
 */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public class StatEntry extends Metric {

	public static final String UNSUPPORTED_VALUE_TYPE_ERROR = "Unsupported ValueType";
	private StatsBuffered buffered;
	private final Function<Double, StatsBuffered> init;
	private final Consumer<Double> reset;
	private final Supplier<Object> statsStringSupplier;
	private final Supplier<Object> resetStatsStringSupplier;

	private Object snapshot;
	private double snapshotMax;
	private double snapshotMin;
	private double snapshotStdDev;

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
	 * Getter for {@code buffered}
	 *
	 * @return the {@link StatsBuffered}, if available, otherwise {@code null}
	 */
	public StatsBuffered getBuffered() {
		return buffered;
	}

	/**
	 * Getter for {@code init}, a lambda that instantiates and initializes, using the given half life
	 *
	 * @return the initialization-lambda, if available, otherwise {@code null}
	 */
	public Function<Double, StatsBuffered> getInit() {
		return init;
	}

	/**
	 * Getter for {@code reset}, a lambda that resets the metric, using the given half life
	 *
	 * @return the reset-lambda, if available, otherwise {@code null}
	 */
	public Consumer<Double> getReset() {
		return reset;
	}

	/**
	 * Getter for {@code statsStringSupplier}, a lambda that returns the metric value
	 *
	 * @return the lambda
	 */
	public Supplier<Object> getStatsStringSupplier() {
		return statsStringSupplier;
	}

	/**
	 * Getter for {@code resetStatsStringSupplier}, a lambda that returns the statistic string
	 * and resets it at the same time
	 *
	 * @return the lambda
	 */
	public Supplier<Object> getResetStatsStringSupplier() {
		return resetStatsStringSupplier;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ValueType> getValueTypes() {
		return buffered == null? Metric.VALUE_TYPE : Metric.DISTRIBUTION_TYPES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object get(final ValueType valueType) {
		CommonUtils.throwArgNull(valueType, "valueType");
		if (buffered == null) {
			if (valueType == VALUE) {
				return statsStringSupplier.get();
			}
			throw new IllegalArgumentException(UNSUPPORTED_VALUE_TYPE_ERROR);
		} else {
			return switch (valueType) {
				case VALUE -> statsStringSupplier.get();
				case MAX -> buffered.getMax();
				case MIN -> buffered.getMin();
				case STD_DEV -> buffered.getStdDev();
				default -> throw new IllegalArgumentException(UNSUPPORTED_VALUE_TYPE_ERROR);
			};
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("removal")
	@Override
	public List<Pair<ValueType, Object>> takeSnapshot() {
		if (buffered == null) {
			return List.of(Pair.of(VALUE, resetStatsStringSupplier.get()));
		}

		final double max = buffered.getMax();
		final double min = buffered.getMin();
		final double stdDev = buffered.getStdDev();
		final Object value = resetStatsStringSupplier.get();
		return List.of(Pair.of(VALUE, value), Pair.of(MAX, max), Pair.of(MIN, min), Pair.of(STD_DEV, stdDev));
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
	public StatsBuffered getStatsBuffered() {
		return getBuffered();
	}
}

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
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

/**
 * A very flexible implementation of Metric which behavior is mostly passed to it via lambdas.
 *
 * @deprecated This class will be removed. Use one of the specialized {@link Metric}-implementations instead.
 */
@Deprecated(forRemoval = true)
public interface StatEntry extends Metric {

	/**
	 * {@inheritDoc}
	 */
	@Override
	default EnumSet<ValueType> getValueTypes() {
		return getBuffered() == null ? EnumSet.of(VALUE) : EnumSet.of(VALUE, MAX, MIN, STD_DEV);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	default Object get(final ValueType valueType) {
		CommonUtils.throwArgNull(valueType, "valueType");
		if (getBuffered() == null) {
			if (valueType == VALUE) {
				return getStatsStringSupplier().get();
			}
			throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
		} else {
			return switch (valueType) {
				case VALUE -> getStatsStringSupplier().get();
				case MAX -> getBuffered().getMax();
				case MIN -> getBuffered().getMin();
				case STD_DEV -> getBuffered().getStdDev();
				default -> throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
			};
		}
	}

	/**
	 * Getter for {@code buffered}
	 *
	 * @return the {@link StatsBuffered}, if available, otherwise {@code null}
	 */
	StatsBuffered getBuffered();

	/**
	 * Getter for {@code reset}, a lambda that resets the metric, using the given half life
	 *
	 * @return the reset-lambda, if available, otherwise {@code null}
	 */
	Consumer<Double> getReset();

	/**
	 * Getter for {@code statsStringSupplier}, a lambda that returns the metric value
	 *
	 * @return the lambda
	 */
	Supplier<Object> getStatsStringSupplier();

	/**
	 * Getter for {@code resetStatsStringSupplier}, a lambda that returns the statistic string
	 * and resets it at the same time
	 *
	 * @return the lambda
	 */
	Supplier<Object> getResetStatsStringSupplier();

	/**
	 * Configuration of a {@link StatEntry}.
	 */
	final class Config extends MetricConfig<StatEntry, Config> {

		private final StatsBuffered buffered;
		private final Function<Double, StatsBuffered> init;
		private final Consumer<Double> reset;
		private final Supplier<Object> statsStringSupplier;
		private final Supplier<Object> resetStatsStringSupplier;

		/**
		 * stores all the parameters, which can be accessed directly
		 *
		 * @param category
		 * 		the kind of metric (metrics are grouped or filtered by this)
		 * @param name
		 * 		a short name for the metric
		 * @param statsStringSupplier
		 * 		a lambda that returns the metric string
		 * @throws IllegalArgumentException
		 * 		if one of the parameters is {@code null}
		 */
		public Config(final String category, final String name, final Supplier<Object> statsStringSupplier) {
			super(category, name, FloatFormats.FORMAT_11_3);
			this.buffered = null;
			this.init = null;
			this.reset = null;
			this.statsStringSupplier = CommonUtils.throwArgNull(statsStringSupplier, "statsStringSupplier");
			this.resetStatsStringSupplier = statsStringSupplier;
		}

		private Config(
				final String category,
				final String name,
				final String description,
				final String unit,
				final String format,
				final StatsBuffered buffered,
				final Function<Double, StatsBuffered> init,
				final Consumer<Double> reset,
				final Supplier<Object> statsStringSupplier,
				final Supplier<Object> resetStatsStringSupplier) {
			super(category, name, description, unit, format);
			this.buffered = buffered;
			this.init = init;
			this.reset = reset;
			this.statsStringSupplier = CommonUtils.throwArgNull(statsStringSupplier, "statsStringSupplier");
			this.resetStatsStringSupplier = CommonUtils.throwArgNull(resetStatsStringSupplier, "resetStatsStringSupplier");
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public StatEntry.Config withDescription(final String description) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					description,
					getUnit(),
					getFormat(),
					getBuffered(),
					getInit(),
					getReset(),
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public StatEntry.Config withUnit(final String unit) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					unit,
					getFormat(),
					getBuffered(),
					getInit(),
					getReset(),
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
		 *
		 * @param format
		 * 		the format-string
		 * @return a new configuration-object with updated {@code format}
		 * @throws IllegalArgumentException
		 * 		if {@code format} is {@code null} or consists only of whitespaces
		 */
		public StatEntry.Config withFormat(final String format) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					getUnit(),
					format,
					getBuffered(),
					getInit(),
					getReset(),
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * Getter of {@code buffered}
		 *
		 * @return {@code buffered}
		 */
		public StatsBuffered getBuffered() {
			return buffered;
		}

		/**
		 * Fluent-style setter of {@code buffered}.
		 *
		 * @param buffered
		 * 		the {@link StatsBuffered}
		 * @return a reference to {@code this}
		 */
		public StatEntry.Config withBuffered(final StatsBuffered buffered) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					getUnit(),
					getFormat(),
					buffered,
					getInit(),
					getReset(),
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * Getter of {@code init}
		 *
		 * @return {@code init}
		 */
		public Function<Double, StatsBuffered> getInit() {
			return init;
		}

		/**
		 * Fluent-style setter of {@code init}.
		 *
		 * @param init
		 * 		the init-function
		 * @return a reference to {@code this}
		 */
		public StatEntry.Config withInit(final Function<Double, StatsBuffered> init) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					getUnit(),
					getFormat(),
					getBuffered(),
					init,
					getReset(),
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * Getter of {@code reset}
		 *
		 * @return {@code reset}
		 */
		public Consumer<Double> getReset() {
			return reset;
		}

		/**
		 * Fluent-style setter of {@code reset}.
		 *
		 * @param reset
		 * 		the reset-function
		 * @return a reference to {@code this}
		 */
		public StatEntry.Config withReset(final Consumer<Double> reset) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					getUnit(),
					getFormat(),
					getBuffered(),
					getInit(),
					reset,
					getStatsStringSupplier(),
					getResetStatsStringSupplier()
			);
		}

		/**
		 * Getter of {@code statsStringSupplier}
		 *
		 * @return {@code statsStringSupplier}
		 */
		public Supplier<Object> getStatsStringSupplier() {
			return statsStringSupplier;
		}

		/**
		 * Getter of {@code resetStatsStringSupplier}
		 *
		 * @return {@code resetStatsStringSupplier}
		 */
		public Supplier<Object> getResetStatsStringSupplier() {
			return resetStatsStringSupplier;
		}

		/**
		 * Fluent-style setter of {@code resetStatsStringSupplier}.
		 *
		 * @param resetStatsStringSupplier
		 * 		the reset-supplier
		 * @return a reference to {@code this}
		 */
		public StatEntry.Config withResetStatsStringSupplier(final Supplier<Object> resetStatsStringSupplier) {
			return new StatEntry.Config(
					getCategory(),
					getName(),
					getDescription(),
					getUnit(),
					getFormat(),
					getBuffered(),
					getInit(),
					getReset(),
					getStatsStringSupplier(),
					resetStatsStringSupplier
			);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		Class<StatEntry> getResultClass() {
			return StatEntry.class;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		StatEntry create(final MetricsFactory factory) {
			return factory.createStatEntry(this);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return new ToStringBuilder(this)
					.appendSuper(super.toString())
					.toString();
		}
	}

}

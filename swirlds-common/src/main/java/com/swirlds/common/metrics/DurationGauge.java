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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgBlank;

/**
 * Stores a single duration. The output unit is determined by configuration
 */
public interface DurationGauge extends Metric {
	@Override
	default EnumSet<ValueType> getValueTypes() {
		return EnumSet.of(VALUE);
	}

	@Override
	default Double get(final ValueType valueType) {
		if (valueType == VALUE) {
			return get();
		}
		throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
	}

	/**
	 * @return the current stored duration in nanoseconds
	 */
	long getNanos();

	/**
	 * Set the gauge to the value supplied
	 *
	 * @param duration
	 * 		the value to set the gauge to
	 */
	void update(Duration duration);

	/**
	 * Get the current value in units supplied in the constructor
	 *
	 * @return the current value
	 */
	double get();

	/**
	 * Configuration of a {@link DurationGauge}
	 */
	final class Config extends MetricConfig<DurationGauge, DurationGauge.Config> {
		private final ChronoUnit unit;

		/**
		 * Constructor of {@code DoubleGauge.Config}
		 *
		 * @param category
		 * 		the kind of metric (metrics are grouped or filtered by this)
		 * @param name
		 * 		a short name for the metric
		 * @param description
		 * 		a one-sentence description of the metric
		 * @param unit
		 * 		the time unit in which to display this duration
		 * @throws IllegalArgumentException
		 * 		if one of the parameters is {@code null}
		 */
		public Config(
				final String category,
				final String name,
				final String description,
				final ChronoUnit unit) {
			super(category,
					throwArgBlank(name, "name") + " " + getAppendix(unit),
					description,
					unit.name(),
					getFormat(unit));
			this.unit = unit;
		}

		@Override
		public DurationGauge.Config withDescription(final String description) {
			return new DurationGauge.Config(getCategory(), getName(), description, getTimeUnit());
		}

		@Override
		public DurationGauge.Config withUnit(final String unit) {
			throw new UnsupportedOperationException("a String unit is not compatible with this class");
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		Class<DurationGauge> getResultClass() {
			return DurationGauge.class;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		DurationGauge create(final MetricsFactory factory) {
			return factory.createDurationGauge(this);
		}

		public ChronoUnit getTimeUnit() {
			return unit;
		}

		private static String getFormat(final ChronoUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Unit must not be null!");
			}
			return switch (unit) {
				case NANOS, MICROS -> FloatFormats.FORMAT_DECIMAL_0;
				case MILLIS, SECONDS -> FloatFormats.FORMAT_DECIMAL_3;
				default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
			};
		}

		private static String getAppendix(final ChronoUnit unit) {
			if (unit == null) {
				throw new IllegalArgumentException("Unit must not be null!");
			}
			return switch (unit) {
				case NANOS -> "(nanos)";
				case MICROS -> "(micros)";
				case MILLIS -> "(millis)";
				case SECONDS -> "(sec)";
				default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
			};
		}
	}
}

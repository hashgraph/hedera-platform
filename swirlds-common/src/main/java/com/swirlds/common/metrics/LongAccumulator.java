/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.metrics;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.function.LongBinaryOperator;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code LongAccumulator} accumulates a {@code long}-value.
 *
 * <p>It is reset in regular intervals. The exact timing depends on the implementation.
 *
 * <p>A {@code LongAccumulator} is reset to the {@link #getInitialValue() initialValue}. If no
 * {@code initialValue} was specified, the {@code LongAccumulator} is reset to {@code 0L}.
 */
public interface LongAccumulator extends BaseLongMetric {

    /**
     * Returns the {@code initialValue} of the {@code LongAccumulator}
     *
     * @return the initial value
     */
    long getInitialValue();

    /**
     * Atomically updates the current value with the results of applying the {@code operator} of
     * this {@code LongAccumulator} to the current and given value.
     *
     * <p>The function is applied with the current value as its first argument, and the provided
     * {@code other} as the second argument.
     *
     * @param other the update value
     */
    void update(final long other);

    /** Configuration of a {@link LongAccumulator} */
    final class Config extends MetricConfig<LongAccumulator, LongAccumulator.Config> {

        private final LongBinaryOperator accumulator;
        private final long initialValue;

        /**
         * Constructor of {@code LongAccumulator.Config}
         *
         * <p>By default, the {@link #getAccumulator() accumulator} is set to {@code Long::max}, the
         * {@link #getInitialValue() initialValue} is set to {@code 0L}, and {@link #getFormat()
         * format} is set to {@code "%d"}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists
         *     only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, "%d");
            this.accumulator = Long::max;
            this.initialValue = 0L;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final LongBinaryOperator accumulator,
                final long initialValue) {

            super(category, name, description, unit, format);
            this.accumulator = throwArgNull(accumulator, "accumulator");
            this.initialValue = initialValue;
        }

        /** {@inheritDoc} */
        @Override
        public LongAccumulator.Config withDescription(final String description) {
            return new LongAccumulator.Config(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getAccumulator(),
                    getInitialValue());
        }

        /** {@inheritDoc} */
        @Override
        public LongAccumulator.Config withUnit(final String unit) {
            return new LongAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
                    getAccumulator(),
                    getInitialValue());
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException if {@code format} is {@code null} or consists only of
         *     whitespaces
         */
        public LongAccumulator.Config withFormat(final String format) {
            return new LongAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getAccumulator(),
                    getInitialValue());
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         */
        public LongBinaryOperator getAccumulator() {
            return accumulator;
        }

        /**
         * Fluent-style setter of the accumulator.
         *
         * <p>The accumulator should be side-effect-free, since it may be re-applied when attempted
         * updates fail due to contention among threads.
         *
         * @param accumulator The {@link LongBinaryOperator} that is used to accumulate the value.
         * @return a new configuration-object with updated {@code initialValue}
         */
        public LongAccumulator.Config withAccumulator(final LongBinaryOperator accumulator) {
            return new LongAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    accumulator,
                    getInitialValue());
        }

        /**
         * Getter of the {@link LongAccumulator#getInitialValue() initialValue}
         *
         * @return the initial value
         */
        public long getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue the initial value
         * @return a reference to {@code this}
         */
        public LongAccumulator.Config withInitialValue(final long initialValue) {
            return new LongAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getAccumulator(),
                    initialValue);
        }

        /** {@inheritDoc} */
        @Override
        Class<LongAccumulator> getResultClass() {
            return LongAccumulator.class;
        }

        /** {@inheritDoc} */
        @Override
        LongAccumulator create(final MetricsFactory factory) {
            return factory.createLongAccumulator(this);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("initialValue", initialValue)
                    .toString();
        }
    }
}

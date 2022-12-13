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

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code IntegerGauge} stores a single {@code int} value.
 *
 * <p>Only the current value is stored, no history or distribution is kept.
 */
public interface IntegerGauge extends BaseIntegerMetric {

    /**
     * Set the current value
     *
     * @param newValue the new value
     */
    void set(final int newValue);

    /** Configuration of a {@link IntegerGauge} */
    final class Config extends MetricConfig<IntegerGauge, IntegerGauge.Config> {

        private final int initialValue;

        /**
         * Constructor of {@code IntegerGauge.Config}
         *
         * <p>The {@link #getInitialValue() initialValue} is by default set to {@code 0}, the {@link
         * #getFormat() format} is set to "%d".
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists
         *     only of whitespaces
         */
        public Config(final String category, final String name) {

            super(category, name, "%d");
            this.initialValue = 0;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final int initialValue) {

            super(category, name, description, unit, format);
            this.initialValue = initialValue;
        }

        /** {@inheritDoc} */
        @Override
        public IntegerGauge.Config withDescription(final String description) {
            return new IntegerGauge.Config(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getInitialValue());
        }

        /** {@inheritDoc} */
        @Override
        public IntegerGauge.Config withUnit(final String unit) {
            return new IntegerGauge.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
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
        public IntegerGauge.Config withFormat(final String format) {
            return new IntegerGauge.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getInitialValue());
        }

        /**
         * Getter of the {@code initialValue}
         *
         * @return the {@code initialValue}
         */
        public int getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue the initial value
         * @return a new configuration-object with updated {@code initialValue}
         */
        public IntegerGauge.Config withInitialValue(final int initialValue) {
            return new IntegerGauge.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    initialValue);
        }

        /** {@inheritDoc} */
        @Override
        Class<IntegerGauge> getResultClass() {
            return IntegerGauge.class;
        }

        /** {@inheritDoc} */
        @Override
        IntegerGauge create(final MetricsFactory factory) {
            return factory.createIntegerGauge(this);
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

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
 * A {@code DoubleGauge} stores a single {@code double} value.
 *
 * <p>Only the current value is stored, no history or distribution is kept. Special values ({@link
 * Double#NaN}, {@link Double#POSITIVE_INFINITY}, {@link Double#NEGATIVE_INFINITY}) are supported.
 */
public interface DoubleGauge extends BaseDoubleMetric {

    /**
     * Set the current value
     *
     * <p>{@link Double#NaN}, {@link Double#POSITIVE_INFINITY}, {@link Double#NEGATIVE_INFINITY} are
     * supported.
     *
     * @param newValue the new value
     */
    void set(final double newValue);

    /** Configuration of a {@link DoubleGauge} */
    final class Config extends MetricConfig<DoubleGauge, DoubleGauge.Config> {

        private final double initialValue;

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * <p>The {@link #getInitialValue() initialValue} is by default set to {@code 0.0}
         *
         * <p>The initial value is set to {@code 0.0}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists
         *     only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, FloatFormats.FORMAT_11_3);
            this.initialValue = 0.0;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final double initialValue) {

            super(category, name, description, unit, format);
            this.initialValue = initialValue;
        }

        /** {@inheritDoc} */
        @Override
        public DoubleGauge.Config withDescription(final String description) {
            return new DoubleGauge.Config(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getInitialValue());
        }

        /** {@inheritDoc} */
        @Override
        public DoubleGauge.Config withUnit(final String unit) {
            return new DoubleGauge.Config(
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
        public DoubleGauge.Config withFormat(final String format) {
            return new DoubleGauge.Config(
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
        public double getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue the initial value
         * @return a new configuration-object with updated {@code initialValue}
         */
        public DoubleGauge.Config withInitialValue(final double initialValue) {
            return new DoubleGauge.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    initialValue);
        }

        /** {@inheritDoc} */
        @Override
        Class<DoubleGauge> getResultClass() {
            return DoubleGauge.class;
        }

        /** {@inheritDoc} */
        @Override
        DoubleGauge create(final MetricsFactory factory) {
            return factory.createDoubleGauge(this);
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

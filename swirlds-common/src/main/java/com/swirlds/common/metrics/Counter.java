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

import static com.swirlds.common.metrics.Metric.ValueType.COUNTER;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.common.utility.CommonUtils;
import java.util.EnumSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A {@code Counter} can be used to count events and similar things.
 *
 * <p>The value of a {@code Counter} is initially {@code 0} and can only be increased.
 */
public interface Counter extends Metric {

    /** {@inheritDoc} */
    @Override
    default DataType getDataType() {
        return DataType.INT;
    }

    /** {@inheritDoc} */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(COUNTER);
    }

    /** {@inheritDoc} */
    @Override
    default Long get(final ValueType valueType) {
        CommonUtils.throwArgNull(valueType, "valueType");
        // Metric.get(ValueType.VALUE) should always work and return the main value of a Metric.
        // Therefore, we allow it here, too.
        if ((valueType == COUNTER) || (valueType == VALUE)) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * Return the current value of the {@code Counter}
     *
     * @return the current value
     */
    long get();

    /**
     * Add a value to the {@code Counter}.
     *
     * <p>The value of a {@code Counter} can only increase, thus only non-negative numbers can be
     * added.
     *
     * @param value the value that needs to be added
     * @throws IllegalArgumentException if {@code value <= 0}
     */
    void add(final long value);

    /** Increase the {@code Counter} by {@code 1}. */
    void increment();

    /** Configuration of a {@link Counter}. */
    final class Config extends MetricConfig<Counter, Counter.Config> {

        /**
         * Constructor of {@code Counter.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists
         *     only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, "%d");
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit) {
            super(category, name, description, unit, "%d");
        }

        /** {@inheritDoc} */
        @Override
        public Counter.Config withDescription(final String description) {
            return new Counter.Config(getCategory(), getName(), description, getUnit());
        }

        /** {@inheritDoc} */
        @Override
        public Counter.Config withUnit(final String unit) {
            return new Counter.Config(getCategory(), getName(), getDescription(), unit);
        }

        /** {@inheritDoc} */
        @Override
        Class<Counter> getResultClass() {
            return Counter.class;
        }

        /** {@inheritDoc} */
        @Override
        Counter create(final MetricsFactory factory) {
            return factory.createCounter(this);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return new ToStringBuilder(this).appendSuper(super.toString()).toString();
        }
    }
}

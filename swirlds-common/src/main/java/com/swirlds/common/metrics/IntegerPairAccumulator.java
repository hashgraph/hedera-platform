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

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.EnumSet;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code IntegerPairAccumulator} accumulates two {@code int}-values, which can be updated
 * atomically.
 *
 * <p>When reading the value of an {@code IntegerPairAccumulator}, both {@code ints} are combined.
 *
 * <p>An {@code IntegerAccumulator} is reset in regular intervals. The exact timing depends on the
 * implementation.
 *
 * @param <T> The type of the combined value
 */
public interface IntegerPairAccumulator<T> extends Metric {

    BiFunction<Integer, Integer, Double> AVERAGE =
            (sum, count) -> {
                if (count == 0) {
                    // avoid division by 0
                    return 0.0;
                }
                return ((double) sum) / count;
            };

    /** {@inheritDoc} */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /** {@inheritDoc} */
    @Override
    default T get(final ValueType valueType) {
        if (valueType == VALUE) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * Get the current value
     *
     * @return the current value
     */
    T get();

    /**
     * Returns the left {@code int}-value
     *
     * @return the left value
     */
    int getLeft();

    /**
     * Returns the right {@code int}-value
     *
     * @return the right value
     */
    int getRight();

    /**
     * Atomically updates the current values with the results of applying the {@code accumulators}
     * of this {@code IntegerPairAccumulator} to the current and given values.
     *
     * <p>The function is applied with the current values as their first argument, and the provided
     * {@code leftValue} and {@code rightValue }as the second arguments.
     *
     * @param leftValue the value combined with the left {@code int}
     * @param rightValue the value combined with the right {@code int}
     */
    void update(final int leftValue, final int rightValue);

    /** Configuration of a {@link IntegerPairAccumulator} */
    final class Config<T>
            extends MetricConfig<IntegerPairAccumulator<T>, IntegerPairAccumulator.Config<T>> {

        private final Class<T> type;

        private final BiFunction<Integer, Integer, T> resultFunction;

        private final IntBinaryOperator leftAccumulator;
        private final IntBinaryOperator rightAccumulator;

        /**
         * Constructor of {@code IntegerPairAccumulator.Config}
         *
         * <p>The accumulators are by default set to {@code Integer::sum}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param type the type of the values this {@code IntegerPairAccumulator} returns
         * @param resultFunction the function that is used to calculate the {@code
         *     IntegerAccumulator}'s value.
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists
         *     only of whitespaces
         */
        public Config(
                final String category,
                final String name,
                final Class<T> type,
                final BiFunction<Integer, Integer, T> resultFunction) {

            super(category, name, "%s");
            this.type = throwArgNull(type, "type");
            this.resultFunction = throwArgNull(resultFunction, "resultFunction");
            this.leftAccumulator = Integer::sum;
            this.rightAccumulator = Integer::sum;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final Class<T> type,
                final BiFunction<Integer, Integer, T> resultFunction,
                final IntBinaryOperator leftAccumulator,
                final IntBinaryOperator rightAccumulator) {

            super(category, name, description, unit, format);
            this.type = throwArgNull(type, "type");
            this.resultFunction = throwArgNull(resultFunction, "resultFunction");
            this.leftAccumulator = throwArgNull(leftAccumulator, "leftAccumulator");
            this.rightAccumulator = throwArgNull(rightAccumulator, "rightAccumulator");
        }

        /** {@inheritDoc} */
        @Override
        public IntegerPairAccumulator.Config<T> withDescription(final String description) {
            return new IntegerPairAccumulator.Config<>(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getType(),
                    getResultFunction(),
                    getLeftAccumulator(),
                    getRightAccumulator());
        }

        /** {@inheritDoc} */
        @Override
        public IntegerPairAccumulator.Config<T> withUnit(final String unit) {
            return new IntegerPairAccumulator.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
                    getType(),
                    getResultFunction(),
                    getLeftAccumulator(),
                    getRightAccumulator());
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException if {@code format} is {@code null} or consists only of
         *     whitespaces
         */
        public IntegerPairAccumulator.Config<T> withFormat(final String format) {
            return new IntegerPairAccumulator.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getType(),
                    getResultFunction(),
                    getLeftAccumulator(),
                    getRightAccumulator());
        }

        /**
         * Getter of the type of the returned values
         *
         * @return the type of the returned values
         */
        public Class<T> getType() {
            return type;
        }

        /**
         * Getter of the {@code leftAccumulator}
         *
         * @return the {@code leftAccumulator}
         */
        public IntBinaryOperator getLeftAccumulator() {
            return leftAccumulator;
        }

        /**
         * Fluent-style setter of the left accumulator.
         *
         * @param leftAccumulator the left accumulator
         * @return a new configuration-object with updated {@code leftAccumulator}
         */
        public IntegerPairAccumulator.Config<T> withLeftAccumulator(
                final IntBinaryOperator leftAccumulator) {
            return new IntegerPairAccumulator.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    getResultFunction(),
                    leftAccumulator,
                    getRightAccumulator());
        }

        /**
         * Getter of the {@code rightAccumulator}
         *
         * @return the {@code rightAccumulator}
         */
        public IntBinaryOperator getRightAccumulator() {
            return rightAccumulator;
        }

        /**
         * Fluent-style setter of the right accumulator.
         *
         * @param rightAccumulator the right accumulator value
         * @return a new configuration-object with updated {@code rightAccumulator}
         */
        public IntegerPairAccumulator.Config<T> withRightAccumulator(
                final IntBinaryOperator rightAccumulator) {
            return new IntegerPairAccumulator.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    getResultFunction(),
                    getLeftAccumulator(),
                    rightAccumulator);
        }

        /**
         * Getter of the {@code resultFunction}
         *
         * @return the {@code resultFunction}
         */
        public BiFunction<Integer, Integer, T> getResultFunction() {
            return resultFunction;
        }

        /** {@inheritDoc} */
        @SuppressWarnings("unchecked")
        @Override
        Class<IntegerPairAccumulator<T>> getResultClass() {
            return (Class<IntegerPairAccumulator<T>>) (Class<?>) IntegerPairAccumulator.class;
        }

        /** {@inheritDoc} */
        @Override
        IntegerPairAccumulator<T> create(final MetricsFactory factory) {
            return factory.createIntegerPairAccumulator(this);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("type", type.getName())
                    .toString();
        }
    }
}

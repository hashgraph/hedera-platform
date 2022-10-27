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

/**
 * Factory for all {@link Metric}-implementations
 */
public interface MetricsFactory {

	/**
	 * Creates a {@link Counter}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code Counter}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	Counter createCounter(final Counter.Config config);

	/**
	 * Creates a {@link DoubleAccumulator}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code DoubleAccumulator}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	DoubleAccumulator createDoubleAccumulator(final DoubleAccumulator.Config config);

	/**
	 * Creates a {@link DoubleGauge}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code DoubleGauge}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	DoubleGauge createDoubleGauge(final DoubleGauge.Config config);

	/**
	 * Creates a {@link DurationGauge}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@link DurationGauge}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	DurationGauge createDurationGauge(final DurationGauge.Config config);

	/**
	 * Creates a {@link FunctionGauge}
	 *
	 * @param config
	 * 		the configuration
	 * @param <T>
	 * 		the type of the value that will be contained in the {@code FunctionGauge}
	 * @return the new {@code FunctionGauge}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	<T> FunctionGauge<T> createFunctionGauge(final FunctionGauge.Config<T> config);

	/**
	 * Creates a {@link IntegerAccumulator}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code IntegerAccumulator}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	IntegerAccumulator createIntegerAccumulator(IntegerAccumulator.Config config);

	/**
	 * Creates a {@link IntegerGauge}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code IntegerGauge}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	IntegerGauge createIntegerGauge(final IntegerGauge.Config config);

	/**
	 * Creates a {@link IntegerPairAccumulator}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code IntegerPairAccumulator}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	<T> IntegerPairAccumulator<T> createIntegerPairAccumulator(IntegerPairAccumulator.Config<T> config);

	/**
	 * Creates a {@link LongAccumulator}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code LongAccumulator}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	LongAccumulator createLongAccumulator(LongAccumulator.Config config);

	/**
	 * Creates a {@link LongGauge}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code LongGauge}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	LongGauge createLongGauge(final LongGauge.Config config);

	/**
	 * Creates a {@link RunningAverageMetric}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code RunningAverageMetric}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	RunningAverageMetric createRunningAverageMetric(final RunningAverageMetric.Config config);

	/**
	 * Creates a {@link SpeedometerMetric}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code SpeedometerMetric}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	SpeedometerMetric createSpeedometerMetric(final SpeedometerMetric.Config config);

	/**
	 * Creates a {@link StatEntry}
	 *
	 * @param config
	 * 		the configuration
	 * @return the new {@code StatEntry}
	 * @throws IllegalArgumentException
	 * 		if {@code config} is {@code null}
	 */
	@SuppressWarnings("removal")
	StatEntry createStatEntry(final StatEntry.Config config);

}

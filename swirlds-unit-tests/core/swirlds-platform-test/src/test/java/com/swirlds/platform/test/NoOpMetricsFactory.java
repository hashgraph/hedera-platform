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
package com.swirlds.platform.test;

import static org.mockito.Mockito.mock;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.DoubleAccumulator;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.LongGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import java.util.concurrent.ScheduledExecutorService;

/** An implementation of {@link MetricsFactory} that creates no-op {@link Metric} instances */
public final class NoOpMetricsFactory implements MetricsFactory {

    /** Get a new no-op metrics instance. */
    public static Metrics noOpMetrics() {
        return new Metrics(mock(ScheduledExecutorService.class), new NoOpMetricsFactory());
    }

    private NoOpMetricsFactory() {}

    /** {@inheritDoc} */
    @Override
    public Counter createCounter(final Counter.Config config) {
        return mock(Counter.class);
    }

    /** {@inheritDoc} */
    @Override
    public DoubleAccumulator createDoubleAccumulator(final DoubleAccumulator.Config config) {
        return mock(DoubleAccumulator.class);
    }

    /** {@inheritDoc} */
    @Override
    public DoubleGauge createDoubleGauge(final DoubleGauge.Config config) {
        return mock(DoubleGauge.class);
    }

    @Override
    public DurationGauge createDurationGauge(final DurationGauge.Config config) {
        return mock(DurationGauge.class);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <T> FunctionGauge<T> createFunctionGauge(final FunctionGauge.Config<T> config) {
        return mock(FunctionGauge.class);
    }

    /** {@inheritDoc} */
    @Override
    public IntegerAccumulator createIntegerAccumulator(final IntegerAccumulator.Config config) {
        return mock(IntegerAccumulator.class);
    }

    /** {@inheritDoc} */
    @Override
    public IntegerGauge createIntegerGauge(final IntegerGauge.Config config) {
        return mock(IntegerGauge.class);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <T> IntegerPairAccumulator<T> createIntegerPairAccumulator(
            final IntegerPairAccumulator.Config<T> config) {
        return mock(IntegerPairAccumulator.class);
    }

    /** {@inheritDoc} */
    @Override
    public LongAccumulator createLongAccumulator(final LongAccumulator.Config config) {
        return mock(LongAccumulator.class);
    }

    /** {@inheritDoc} */
    @Override
    public LongGauge createLongGauge(final LongGauge.Config config) {
        return mock(LongGauge.class);
    }

    /** {@inheritDoc} */
    @Override
    public RunningAverageMetric createRunningAverageMetric(
            final RunningAverageMetric.Config config) {
        return mock(RunningAverageMetric.class);
    }

    /** {@inheritDoc} */
    @Override
    public SpeedometerMetric createSpeedometerMetric(final SpeedometerMetric.Config config) {
        return mock(SpeedometerMetric.class);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("removal")
    @Override
    public StatEntry createStatEntry(final StatEntry.Config config) {
        return mock(StatEntry.class);
    }
}

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
package com.swirlds.common.test.metrics.platform.prometheus;

import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
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
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.metrics.platform.DefaultCounter;
import com.swirlds.common.metrics.platform.DefaultDoubleAccumulator;
import com.swirlds.common.metrics.platform.DefaultDoubleGauge;
import com.swirlds.common.metrics.platform.DefaultDurationGauge;
import com.swirlds.common.metrics.platform.DefaultFunctionGauge;
import com.swirlds.common.metrics.platform.DefaultIntegerAccumulator;
import com.swirlds.common.metrics.platform.DefaultIntegerGauge;
import com.swirlds.common.metrics.platform.DefaultIntegerPairAccumulator;
import com.swirlds.common.metrics.platform.DefaultLongAccumulator;
import com.swirlds.common.metrics.platform.DefaultLongGauge;
import com.swirlds.common.metrics.platform.DefaultRunningAverageMetric;
import com.swirlds.common.metrics.platform.DefaultSpeedometerMetric;
import com.swirlds.common.metrics.platform.DefaultStatEntry;
import com.swirlds.common.metrics.platform.prometheus.BooleanAdapter;
import com.swirlds.common.metrics.platform.prometheus.CounterAdapter;
import com.swirlds.common.metrics.platform.prometheus.DistributionAdapter;
import com.swirlds.common.metrics.platform.prometheus.MetricAdapter;
import com.swirlds.common.metrics.platform.prometheus.NumberAdapter;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint;
import com.swirlds.common.metrics.platform.prometheus.StringAdapter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PrometheusEndpointTest {

    private static final String CATEGORY = "CaTeGoRy";
    private static final String NAME = "NaMe";

    static Stream<Arguments> createParameters() {
        return Stream.of(
                Arguments.of(
                        new DefaultCounter(new Counter.Config(CATEGORY, NAME)),
                        CounterAdapter.class),
                Arguments.of(
                        new DefaultDoubleAccumulator(new DoubleAccumulator.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultDoubleGauge(new DoubleGauge.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultDurationGauge(
                                new DurationGauge.Config(CATEGORY, NAME, ChronoUnit.SECONDS)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultFunctionGauge<>(
                                new FunctionGauge.Config<>(
                                        CATEGORY, NAME, Boolean.class, () -> true)),
                        BooleanAdapter.class),
                Arguments.of(
                        new DefaultFunctionGauge<>(
                                new FunctionGauge.Config<>(
                                        CATEGORY, NAME, Double.class, () -> 0.0)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultFunctionGauge<>(
                                new FunctionGauge.Config<>(
                                        CATEGORY, NAME, String.class, () -> "test")),
                        StringAdapter.class),
                Arguments.of(
                        new DefaultIntegerAccumulator(
                                new IntegerAccumulator.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultIntegerGauge(new IntegerGauge.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultIntegerPairAccumulator<>(
                                new IntegerPairAccumulator.Config<>(
                                        CATEGORY, NAME, Boolean.class, (a, b) -> true)),
                        BooleanAdapter.class),
                Arguments.of(
                        new DefaultIntegerPairAccumulator<>(
                                new IntegerPairAccumulator.Config<>(
                                        CATEGORY, NAME, Double.class, (a, b) -> 0.0)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultIntegerPairAccumulator<>(
                                new IntegerPairAccumulator.Config<>(
                                        CATEGORY, NAME, String.class, (a, b) -> "test")),
                        StringAdapter.class),
                Arguments.of(
                        new DefaultLongAccumulator(new LongAccumulator.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultLongGauge(new LongGauge.Config(CATEGORY, NAME)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultRunningAverageMetric(
                                new RunningAverageMetric.Config(CATEGORY, NAME)),
                        DistributionAdapter.class),
                Arguments.of(
                        new DefaultSpeedometerMetric(new SpeedometerMetric.Config(CATEGORY, NAME)),
                        DistributionAdapter.class),
                Arguments.of(
                        new DefaultStatEntry(
                                new StatEntry.Config<>(CATEGORY, NAME, Boolean.class, () -> true)),
                        BooleanAdapter.class),
                Arguments.of(
                        new DefaultStatEntry(
                                new StatEntry.Config<>(CATEGORY, NAME, Double.class, () -> 0.0)),
                        NumberAdapter.class),
                Arguments.of(
                        new DefaultStatEntry(
                                new StatEntry.Config<>(CATEGORY, NAME, String.class, () -> "test")),
                        StringAdapter.class));
    }

    @ParameterizedTest
    @MethodSource("createParameters")
    void testCreateAdapter(final Metric metric, Class<? extends MetricAdapter> clazz)
            throws IOException {
        // given
        final InetSocketAddress address = new InetSocketAddress(0);
        final HttpServer httpServer = HttpServer.create(address, 1);

        try (final PrometheusEndpoint endpoint = new PrometheusEndpoint(httpServer)) {
            // when
            endpoint.createAdapter(metric, GLOBAL);

            // then
            assertThat(endpoint.getAdapter(metric)).isInstanceOf(clazz);
        }
    }
}

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
package com.swirlds.common.test.metrics;

import static com.swirlds.common.metrics.platform.SnapshotService.createGlobalSnapshotService;
import static com.swirlds.common.metrics.platform.SnapshotService.createPlatformSnapshotService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.platform.DefaultMetric;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.metrics.platform.SnapshotReceiver;
import com.swirlds.common.metrics.platform.SnapshotService;
import com.swirlds.common.test.FakeTime;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnapshotServiceTest {

    @Test
    void testRunState() throws InterruptedException {
        // given
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        final ScheduledExecutorService executorService =
                Executors.newSingleThreadScheduledExecutor();

        try {
            final SnapshotService service =
                    createGlobalSnapshotService(metrics, executorService, OSTime.getInstance());

            // initial state
            assertFalse(service.isRunning(), "Service should not run right after initialization");

            // when
            service.start();

            // then
            assertTrue(service.isRunning(), "Service should run");
            assertThrows(
                    IllegalStateException.class,
                    service::start,
                    "Trying to start the service again should throw an IllegalStateException");

            // when
            service.shutdown();

            // then
            assertFalse(service.isRunning(), "Service should not run");
            assertDoesNotThrow(
                    service::shutdown, "Trying to shutdown the service again should be a no-op");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void testGlobalServiceInit() {
        // given
        final Metric metric = mock(Metric.class);
        final List<Metric> metricList = new ArrayList<>();
        metricList.add(metric);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        when(metrics.getAll()).thenReturn(metricList);
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final SnapshotService service =
                createGlobalSnapshotService(metrics, executorService, OSTime.getInstance());
        final SnapshotReceiver receiver = mock(SnapshotReceiver.class);
        service.addSnapshotReceiver(receiver);

        // when
        service.start();
        // we change the original list, the receiver should have received a copy
        metricList.add(mock(Metric.class));

        // then
        verify(receiver).init(List.of(metric));
    }

    @Test
    void testPlatformServiceInit() {
        // given
        final Metric metric = mock(Metric.class);
        final List<Metric> metricList = new ArrayList<>();
        metricList.add(metric);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        when(metrics.getAll()).thenReturn(metricList);

        final Metric globalMetric = mock(Metric.class);
        final List<Metric> globalMetricList = new ArrayList<>();
        globalMetricList.add(globalMetric);
        final DefaultMetrics globalMetrics = mock(DefaultMetrics.class);
        when(globalMetrics.getAll()).thenReturn(globalMetricList);
        when(globalMetrics.getSnapshotService()).thenReturn(mock(SnapshotService.class));

        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final SnapshotService service =
                createPlatformSnapshotService(
                        metrics, executorService, globalMetrics, OSTime.getInstance());
        final SnapshotReceiver receiver = mock(SnapshotReceiver.class);
        service.addSnapshotReceiver(receiver);

        // when
        service.start();
        // we change the original lists, the receiver should have received a copy
        metricList.add(mock(Metric.class));
        globalMetricList.add(mock(Metric.class));

        // then
        verify(receiver).init(List.of(metric, globalMetric));
    }

    @Test
    void testGlobalServiceLoop() {
        // given
        final DefaultMetric metric = mock(DefaultMetric.class);
        when(metric.takeSnapshot())
                .thenReturn(
                        List.of(new Snapshot.SnapshotEntry(Metric.ValueType.VALUE, new Object())));
        final List<Metric> metricList = new ArrayList<>();
        metricList.add(metric);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        when(metrics.getAll()).thenReturn(metricList);
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final SnapshotService service =
                createGlobalSnapshotService(metrics, executorService, OSTime.getInstance());
        final SnapshotReceiver receiver = mock(SnapshotReceiver.class);
        service.addSnapshotReceiver(receiver);
        service.start();

        // when
        service.mainLoop();
        // we change the original list, the receiver should have received a copy
        metricList.add(mock(Metric.class));

        // then
        verify(receiver).handleSnapshots(List.of(Snapshot.of(metric)));
    }

    @Test
    void testPlatformServiceLoop() {
        // given
        final DefaultMetric metric = mock(DefaultMetric.class);
        when(metric.takeSnapshot())
                .thenReturn(
                        List.of(new Snapshot.SnapshotEntry(Metric.ValueType.VALUE, new Object())));
        final List<Metric> metricList = new ArrayList<>();
        metricList.add(metric);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        when(metrics.getAll()).thenReturn(metricList);

        final DefaultMetric globalMetric = mock(DefaultMetric.class);
        when(globalMetric.takeSnapshot())
                .thenReturn(
                        List.of(new Snapshot.SnapshotEntry(Metric.ValueType.VALUE, new Object())));
        final List<Metric> globalMetricList = new ArrayList<>();
        globalMetricList.add(globalMetric);
        final DefaultMetrics globalMetrics = mock(DefaultMetrics.class);
        when(globalMetrics.getAll()).thenReturn(globalMetricList);
        when(globalMetrics.getSnapshotService()).thenReturn(mock(SnapshotService.class));

        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final SnapshotService globalService =
                createGlobalSnapshotService(globalMetrics, executorService, OSTime.getInstance());
        when(globalMetrics.getSnapshotService()).thenReturn(globalService);
        final SnapshotService platformService =
                createPlatformSnapshotService(
                        metrics, executorService, globalMetrics, OSTime.getInstance());
        final SnapshotReceiver receiver = mock(SnapshotReceiver.class);
        platformService.addSnapshotReceiver(receiver);
        globalService.start();
        globalService.mainLoop();
        platformService.start();

        // when
        platformService.mainLoop();
        // we change the original lists, the receiver should have received a copy
        metricList.add(mock(Metric.class));
        globalMetricList.add(mock(Metric.class));

        // then
        verify(receiver).handleSnapshots(List.of(Snapshot.of(metric), Snapshot.of(globalMetric)));
    }

    @Test
    void testRegularLoopBehavior() {
        // given
        final Duration loopDelay = Duration.ofMillis(SettingsCommon.csvWriteFrequency);
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        final Time time = new FakeTime(Duration.ofMillis(100));
        final SnapshotService service = createGlobalSnapshotService(metrics, executorService, time);

        // when
        service.start();

        // then
        final ArgumentCaptor<Runnable> mainLoop = ArgumentCaptor.forClass(Runnable.class);
        final ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<TimeUnit> timeUnit = ArgumentCaptor.forClass(TimeUnit.class);
        verify(executorService).schedule(mainLoop.capture(), delay.capture(), timeUnit.capture());
        final Duration initialDelay =
                Duration.of(delay.getValue(), timeUnit.getValue().toChronoUnit());
        assertThat(initialDelay).isCloseTo(loopDelay, Duration.ofMillis(1));
        reset(executorService);

        // when
        mainLoop.getValue().run();

        // then
        verify(executorService).schedule(any(Runnable.class), delay.capture(), timeUnit.capture());
        final Duration regularDelay =
                Duration.of(delay.getValue(), timeUnit.getValue().toChronoUnit());
        assertThat(regularDelay).isCloseTo(loopDelay.minusMillis(100), Duration.ofMillis(1));
    }

    @Test
    void testLongLoopBehavior() {
        // given
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        final Time time = new FakeTime(Duration.ofSeconds(10 * SettingsCommon.csvWriteFrequency));
        final SnapshotService service = createGlobalSnapshotService(metrics, executorService, time);
        final ArgumentCaptor<Runnable> mainLoop = ArgumentCaptor.forClass(Runnable.class);
        final ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<TimeUnit> timeUnit = ArgumentCaptor.forClass(TimeUnit.class);
        service.start();
        verify(executorService).schedule(mainLoop.capture(), anyLong(), any());
        reset(executorService);

        // when
        mainLoop.getValue().run();

        // then
        verify(executorService).schedule(any(Runnable.class), delay.capture(), timeUnit.capture());
        final Duration longLoopDelay =
                Duration.of(delay.getValue(), timeUnit.getValue().toChronoUnit());
        assertThat(longLoopDelay).isCloseTo(Duration.ofMillis(0), Duration.ofMillis(1));
    }

    @Test
    void testLongPauseBehavior() {
        // given
        final Duration loopDelay = Duration.ofMillis(SettingsCommon.csvWriteFrequency);
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final DefaultMetrics metrics = mock(DefaultMetrics.class);
        final Time time = new FakeTime(Duration.ofMillis(100));
        final SnapshotService service = createGlobalSnapshotService(metrics, executorService, time);
        final ArgumentCaptor<Runnable> mainLoop = ArgumentCaptor.forClass(Runnable.class);
        final ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        final ArgumentCaptor<TimeUnit> timeUnit = ArgumentCaptor.forClass(TimeUnit.class);
        service.start();
        verify(executorService).schedule(mainLoop.capture(), anyLong(), any());
        reset(executorService);

        // when
        mainLoop.getValue().run();

        // then
        verify(executorService).schedule(any(Runnable.class), delay.capture(), timeUnit.capture());
        final Duration regularDelay =
                Duration.of(delay.getValue(), timeUnit.getValue().toChronoUnit());
        assertThat(regularDelay).isCloseTo(loopDelay.minusMillis(100), Duration.ofMillis(1));
    }
}

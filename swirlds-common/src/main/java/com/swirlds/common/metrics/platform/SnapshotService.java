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
package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.platform.DefaultMetrics.EXCEPTION_RATE_THRESHOLD;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.Startable;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import com.swirlds.common.utility.Units;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service that orchestrates different receivers of metrics-snapshots, in particular those that
 * write metrics-data to different file formats.
 *
 * <p>This class contains only the general functionality, handling the data is left to the different
 * implementations of {@link SnapshotReceiver}.
 *
 * <p>This class uses a provided {@link java.util.concurrent.ExecutorService} that triggers all
 * writers in regular intervals. The frequency of these write operations can be configured with
 * {@link SettingsCommon#csvWriteFrequency}.
 *
 * <p>The service is not automatically started, but has to be started manually with {@link
 * #start()}. When done, the service can be shutdown with {@link #shutdown()}.
 *
 * @see SnapshotReceiver
 * @see LegacyCsvWriter
 */
@SuppressWarnings("unused")
public class SnapshotService implements Startable {

    private static final Logger LOG = LogManager.getLogger(SnapshotService.class);

    private final DefaultMetrics metrics;
    private final DefaultMetrics globalMetrics;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Time time;
    private final long delayNanos;

    private final Queue<SnapshotReceiver> receivers = new ConcurrentLinkedQueue<>();
    private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter =
            new ThresholdLimitingHandler<>(EXCEPTION_RATE_THRESHOLD);

    private final SnapshotCache cache = new SnapshotCache();

    /**
     * Constructor of the {@code SnapshotService}.
     *
     * <p>The service is created, but not automatically started. It can be started with {@link
     * #start()}.
     *
     * @param metrics a list of {@link Metric}-instances which values need to be written
     * @param executor the {@link ScheduledExecutorService} that will be used to schedule the
     *     writer-tasks
     * @param time the {@link Time} used for scheduling
     * @throws IllegalArgumentException if {@code metrics}, {@code executor} or {@code time} is
     *     {@code null}
     */
    public static SnapshotService createGlobalSnapshotService(
            final DefaultMetrics metrics,
            final ScheduledExecutorService executor,
            final Time time) {
        return new SnapshotService(metrics, executor, null, time);
    }

    /**
     * Creates a Platform-{@code SnapshotService}.
     *
     * <p>The service is created, but not automatically started. It can be started with {@link
     * #start()}.
     *
     * @param metrics a list of {@link Metric}-instances which values need to be written
     * @param executor the {@link ScheduledExecutorService} that will be used to schedule the
     *     writer-tasks
     * @param globalMetrics the {@code SnapshotService} of the global {@link Metrics}. Can be {@code
     *     null}.
     * @param time the {@link Time} used for scheduling
     * @throws IllegalArgumentException if {@code metrics}, {@code executor} or {@code time} is
     *     {@code null}
     */
    public static SnapshotService createPlatformSnapshotService(
            final DefaultMetrics metrics,
            final ScheduledExecutorService executor,
            final DefaultMetrics globalMetrics,
            final Time time) {
        return new SnapshotService(metrics, executor, globalMetrics, time);
    }

    private SnapshotService(
            final DefaultMetrics metrics,
            final ScheduledExecutorService executor,
            final DefaultMetrics globalMetrics,
            final Time time) {
        this.metrics = CommonUtils.throwArgNull(metrics, "metrics");
        this.executor = CommonUtils.throwArgNull(executor, "executor");
        this.globalMetrics = globalMetrics;
        this.time = CommonUtils.throwArgNull(time, "time");
        this.delayNanos = SettingsCommon.csvWriteFrequency * Units.MILLISECONDS_TO_NANOSECONDS;

        if (globalMetrics != null) {
            globalMetrics.getSnapshotService().addSnapshotReceiver(cache);
        }
    }

    /**
     * Checks if the service is running
     *
     * @return {@code true}, if the service is running, {@code false} otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts the service
     *
     * @throws IllegalStateException if the service is running or was even shutdown already
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("The snapshot-service is already running");
        }
        final List<Metric> metricList = new ArrayList<>(metrics.getAll());
        if (globalMetrics != null) {
            metricList.addAll(globalMetrics.getAll());
        }
        for (final SnapshotReceiver receiver : receivers) {
            try {
                receiver.init(metricList);
            } catch (final RuntimeException ex) {
                LOG.error(
                        EXCEPTION.getMarker(),
                        "Exception while trying to prepare writer {}",
                        receiver,
                        ex);
            }
        }
        executor.schedule(this::mainLoop, delayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Requests the shutdown of the service.
     *
     * <p>Calling this method after the service was already shutdown is possible and should have no
     * effect.
     */
    public void shutdown() {
        running.set(false);
    }

    /**
     * Add a receiver that will be called periodically with the list of snapshots.
     *
     * @param receiver the receiver
     * @throws IllegalArgumentException if {@code receiver} is {@code null}
     */
    public void addSnapshotReceiver(final SnapshotReceiver receiver) {
        CommonUtils.throwArgNull(receiver, "receiver");
        receivers.add(receiver);
    }

    /**
     * Remove a receiver.
     *
     * @param receiver the receiver
     * @throws IllegalArgumentException if {@code receiver} is {@code null}
     */
    public void removeSnapshotReceiver(final SnapshotReceiver receiver) {
        CommonUtils.throwArgNull(receiver, "receiver");
        receivers.remove(receiver);
    }

    // Method is public for testing purposes, needs to become package-private once possible
    public void mainLoop() {
        if (!isRunning()) {
            return;
        }
        final long start = time.nanoTime();
        final List<Snapshot> snapshots =
                metrics.getAll().stream()
                        .map(DefaultMetric.class::cast)
                        .map(Snapshot::of)
                        .collect(Collectors.toCollection(ArrayList::new));
        snapshots.addAll(cache.getSnapshots());
        for (final SnapshotReceiver receiver : receivers) {
            try {
                receiver.handleSnapshots(snapshots);
            } catch (final RuntimeException e) {
                // Ensure that the service continues and other output is still generated
                exceptionRateLimiter.handle(
                        e,
                        error ->
                                LOG.error(
                                        EXCEPTION.getMarker(),
                                        "Exception while trying to write to writer {}",
                                        receiver,
                                        e));
            }
            if (!isRunning()) {
                return;
            }
        }
        final long delta = time.nanoTime() - start;
        executor.schedule(this::mainLoop, Math.max(0L, delayNanos - delta), TimeUnit.NANOSECONDS);
    }
}

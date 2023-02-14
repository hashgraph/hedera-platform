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

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.utility.CommonUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Default implementation of the {@link Metrics} interface. */
public class DefaultMetrics implements Metrics {

    /**
     * Threshold for the number of similar {@link Exception} that are thrown by regular
     * metrics-tasks
     */
    public static final int EXCEPTION_RATE_THRESHOLD = 10;

    private static final String CATEGORY = "category";
    private static final String NAME = "name";

    private final NavigableMap<String, Metric> metricMap = new ConcurrentSkipListMap<>();
    private final Collection<Metric> metricsView =
            Collections.unmodifiableCollection(metricMap.values());
    private final MetricsFactory factory;

    private final MetricsUpdateService updateService;
    private volatile SnapshotService snapshotService;

    /**
     * Constructor of {@code DefaultMetrics}
     *
     * @param executor the {@link ScheduledExecutorService} that will be used by this {@code
     *     DefaultMetrics}
     * @param factory the {@link MetricsFactory} that will be used to create new instances of {@link
     *     Metric}
     */
    public DefaultMetrics(final ScheduledExecutorService executor, final MetricsFactory factory) {
        this.factory = CommonUtils.throwArgNull(factory, "factory");
        this.updateService =
                SettingsCommon.metricsUpdatePeriodMillis <= 0
                        ? null
                        : new MetricsUpdateService(
                                executor,
                                SettingsCommon.metricsUpdatePeriodMillis,
                                TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the {@link SnapshotService} that distributes snapshots of this {@link Metrics}
     *
     * @return the {@code SnapshotService}
     */
    public SnapshotService getSnapshotService() {
        return snapshotService;
    }

    /**
     * Set the {@link SnapshotService}. This method should only be called once during startup.
     *
     * @param snapshotService the {@code SnapshotService}
     */
    public void setSnapshotService(final SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** {@inheritDoc} */
    @Override
    public Metric getMetric(final String category, final String name) {
        CommonUtils.throwArgNull(category, CATEGORY);
        CommonUtils.throwArgNull(name, NAME);
        return metricMap.get(calculateMetricKey(category, name));
    }

    /** {@inheritDoc} */
    @Override
    public Collection<Metric> findMetricsByCategory(final String category) {
        CommonUtils.throwArgNull(category, CATEGORY);
        final String start = category + ".";
        // The character '/' is the successor of '.' in Unicode. We use it to define the first
        // metric-key,
        // which is not part of the result set anymore.
        final String end = category + "/";
        return Collections.unmodifiableCollection(metricMap.subMap(start, end).values());
    }

    /** {@inheritDoc} */
    @Override
    public Collection<Metric> getAll() {
        return metricsView;
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config) {
        CommonUtils.throwArgNull(config, "config");

        final Metric metric =
                metricMap.computeIfAbsent(
                        calculateMetricKey(config), key -> factory.createMetric(config));
        final Class<T> clazz = config.getResultClass();
        if (clazz.isInstance(metric)) {
            return clazz.cast(metric);
        }
        throw new IllegalStateException(
                "A metric with this category and name exists, but it has a different type: "
                        + metric);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final String category, final String name) {
        CommonUtils.throwArgNull(category, CATEGORY);
        CommonUtils.throwArgNull(name, NAME);
        metricMap.remove(calculateMetricKey(category, name));
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final Metric metric) {
        CommonUtils.throwArgNull(metric, "metric");
        metricMap.computeIfPresent(
                calculateMetricKey(metric),
                (key, oldValue) -> metric.getClass().equals(oldValue.getClass()) ? null : oldValue);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final MetricConfig<?, ?> config) {
        CommonUtils.throwArgNull(config, "config");
        metricMap.computeIfPresent(
                calculateMetricKey(config),
                (key, oldValue) -> config.getResultClass().isInstance(oldValue) ? null : oldValue);
    }

    /** {@inheritDoc} */
    @Override
    public void addUpdater(final Runnable updater) {
        CommonUtils.throwArgNull(updater, "updater");
        if (updateService != null) {
            updateService.addUpdater(updater);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void removeUpdater(final Runnable updater) {
        CommonUtils.throwArgNull(updater, "updater");
        if (updateService != null) {
            updateService.removeUpdater(updater);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        if (snapshotService == null) {
            throw new IllegalStateException("SnapshotService was not set");
        }
        if (updateService != null) {
            updateService.start();
        }
        snapshotService.start();
    }

    /**
     * Shuts down the service
     *
     * @return {@code true} if the shutdown finished on time, {@code false} if the call ran into a
     *     timeout
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    public boolean shutdown() throws InterruptedException {
        snapshotService.shutdown();
        return updateService == null || updateService.shutdown();
    }

    /**
     * Calculates a unique key for a given {@code category} and {@code name}
     *
     * <p>The generated key is compatible with keys generated by {@link #calculateMetricKey(Metric)}
     * and {@link #calculateMetricKey(MetricConfig)}.
     *
     * @param category the {@code category} used in the key
     * @param name the {@code name} used in the key
     * @return the calculated key
     */
    public static String calculateMetricKey(final String category, final String name) {
        return category + "." + name;
    }

    /**
     * Calculates a unique key for a given {@link Metric}
     *
     * <p>The generated key is compatible with keys generated by {@link #calculateMetricKey(String,
     * String)} and {@link #calculateMetricKey(MetricConfig)}.
     *
     * @param metric the {@code Metric} for which the key should be calculated
     * @return the calculated key
     */
    public static String calculateMetricKey(final Metric metric) {
        return calculateMetricKey(metric.getCategory(), metric.getName());
    }

    /**
     * Calculates a unique key for a given {@link MetricConfig}
     *
     * <p>The generated key is compatible with keys generated by {@link #calculateMetricKey(String,
     * String)} and {@link #calculateMetricKey(Metric)}.
     *
     * @param config the {@code MetricConfig} for which the key should be calculated
     * @return the calculated key
     */
    public static String calculateMetricKey(final MetricConfig<?, ?> config) {
        return calculateMetricKey(config.getCategory(), config.getName());
    }
}

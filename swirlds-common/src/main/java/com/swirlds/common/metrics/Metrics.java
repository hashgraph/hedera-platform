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
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry-point to the metrics-system.
 *
 * <p>The {@code Metrics} class provides functionality to add and delete metrics. There are also
 * several methods to request registered metrics.
 *
 * <p>In addition, one can register an updater which will be called once per second to update
 * metrics periodically.
 */
public class Metrics {

    private static final Logger LOG = LogManager.getLogger(Metrics.class);

    public static final String INTERNAL_CATEGORY = "internal";
    public static final String PLATFORM_CATEGORY = "platform";
    public static final String INFO_CATEGORY = "platform.info";

    private static final long EXCEPTION_RATE_THRESHOLD = 10;

    private static final long NO_DELAY = 0L;
    private static final String CATEGORY = "category";
    private static final String NAME = "name";

    private final NavigableMap<String, Metric> metricMap = new ConcurrentSkipListMap<>();
    private final Collection<Metric> metricsView =
            Collections.unmodifiableCollection(metricMap.values());
    private final MetricsFactory factory;
    private final ScheduledExecutorService executor;
    private final Queue<Runnable> updaters = new ConcurrentLinkedQueue<>();
    private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter =
            new ThresholdLimitingHandler<>(EXCEPTION_RATE_THRESHOLD);
    private final AtomicBoolean updatersRunning = new AtomicBoolean();

    /**
     * Constructor of the {@code Metrics} class
     *
     * @param executor the {@link ScheduledExecutorService} that will be used to run updaters once
     *     per second
     * @throws IllegalArgumentException if one of the parameters is {@code null} {@code executor}
     *     may be {@code null}, if regular updates are disabled
     */
    public Metrics(final ScheduledExecutorService executor, final MetricsFactory factory) {
        this.factory = CommonUtils.throwArgNull(factory, "factory");
        this.executor =
                SettingsCommon.metricsUpdatePeriodMillis > 0
                        ? CommonUtils.throwArgNull(executor, "executor")
                        : null;
    }

    /**
     * Get a single {@link Metric} identified by its category and name
     *
     * @param category the category of the wanted category
     * @param name the name of the wanten category
     * @return the {@code Metric} if one is found, {@code null} otherwise
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public Metric getMetric(final String category, final String name) {
        CommonUtils.throwArgNull(category, CATEGORY);
        CommonUtils.throwArgNull(name, NAME);
        return metricMap.get(calculateMetricKey(category, name));
    }

    /**
     * Get all metrics with the given category.
     *
     * <p>Categories are structured hierarchically, e.g. if there are two categories
     * "crypto.signature" and "crypto.digest" one will receive metrics from both categories if
     * searching for "crypto"
     *
     * <p>The returned {@link Collection} is backed by the original data-structure, i.e. future
     * changes are automatically reflected. The {@code Collection} is not modifiable.
     *
     * <p>The returned values are ordered by category and name.
     *
     * @param category the category of the wanted metrics
     * @return all metrics that have the category or a sub-category
     * @throws IllegalArgumentException if {@code category} is {@code null}
     */
    public Collection<Metric> findMetricsByCategory(final String category) {
        CommonUtils.throwArgNull(category, CATEGORY);
        final String start = category + ".";
        // The character '/' is the successor of '.' in Unicode. We use it to define the first
        // metric-key,
        // which is not part of the result set anymore.
        final String end = category + "/";
        return Collections.unmodifiableCollection(metricMap.subMap(start, end).values());
    }

    /**
     * Get a list of all metrics that are currently registered.
     *
     * <p>The returned {@link Collection} is backed by the original data-structure, i.e. future
     * changes are automatically reflected. The {@code Collection} is not modifiable.
     *
     * <p>The returned values are ordered by category and name.
     *
     * @return all registered metrics
     */
    public Collection<Metric> getAll() {
        return metricsView;
    }

    /**
     * Get the value of a metric directly. Calling this method is equivalent to calling {@code
     * getMetric(category, name).get(Metric.ValueType.VALUE)}
     *
     * @param category the category of the wanted category
     * @param name the name of the wanten category
     * @return the {@code value} of the {@link Metric}, if one is found, {@code null} otherwise
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public Object getValue(final String category, final String name) {
        final Metric metric = getMetric(category, name);
        return metric != null ? metric.get(VALUE) : null;
    }

    /** Resets all metrics */
    public void resetAll() {
        for (final Metric metric : metricMap.values()) {
            metric.reset();
        }
    }

    /**
     * Checks if a {@link Metric} with the category and name as specified in the config-object
     * exists and returns it. If there is no such {@code Metric}, a new one is created, registered,
     * and returned.
     *
     * @param config the configuration of the {@code Metric}
     * @param <T> class of the {@code Metric} that will be returned
     * @return the registered {@code Metric} (either existing or newly generated)
     * @throws IllegalArgumentException if {@code config} is {@code null}
     * @throws IllegalStateException if a {@code Metric} with the same category and name exists, but
     *     has a different type
     */
    public <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config) {
        CommonUtils.throwArgNull(config, "config");

        /*
        We use the double-dispatch pattern to create a Metric. This simplifies the API, because it allows us
        to have a single method for all types of metrics. (The alternative would have been a method like
        getOrCreateCounter() for each type of metric.)

        This method here call MetricConfig.create() providing the MetricsFactory. This method is overridden by
        each sub-class of MetricConfig to call the specific method in MetricsFactory, i.e. Counter.Config
        calls MetricsFactory.createCounter().
         */
        final Metric metric =
                metricMap.computeIfAbsent(
                        calculateMetricKey(config), key -> config.create(factory));
        final Class<T> clazz = config.getResultClass();
        if (clazz.isInstance(metric)) {
            return clazz.cast(metric);
        }
        throw new IllegalStateException(
                "A metric with this category and name exists, but it has a different type: "
                        + metric);
    }

    /**
     * Remove the {@link Metric} with the given category and name
     *
     * @param category the category of the {@code Metric}, that should be removed
     * @param name the name of the {@code Metric}, that should be removed
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public void remove(final String category, final String name) {
        CommonUtils.throwArgNull(category, CATEGORY);
        CommonUtils.throwArgNull(name, NAME);
        metricMap.remove(calculateMetricKey(category, name));
    }

    /**
     * Remove the {@link Metric}.
     *
     * <p>Please note that two metrics are equal, if their class, category, and name match.
     *
     * @param metric the {@code Metric}, that should be removed
     * @throws IllegalArgumentException if ({code metric} is {@code null}
     */
    public void remove(final Metric metric) {
        CommonUtils.throwArgNull(metric, "metric");
        metricMap.computeIfPresent(
                calculateMetricKey(metric),
                (key, oldValue) -> metric.getClass().equals(oldValue.getClass()) ? null : oldValue);
    }

    /**
     * Remove the {@link Metric} with the given configuration.
     *
     * <p>Please note that two metrics are equal, if their class, category, and name match.
     *
     * @param config the {@link MetricConfig} of the {@code Metric}, that should be removed
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    public void remove(final MetricConfig<?, ?> config) {
        CommonUtils.throwArgNull(config, "config");
        metricMap.computeIfPresent(
                calculateMetricKey(config),
                (key, oldValue) -> config.getResultClass().isInstance(oldValue) ? null : oldValue);
    }

    /**
     * Add an updater that will be called once per second. An updater should only be used to update
     * metrics regularly.
     *
     * @param updater the updater
     * @throws IllegalArgumentException if {@code updater} is {@code null}
     */
    public void addUpdater(final Runnable updater) {
        CommonUtils.throwArgNull(updater, "updater");
        updaters.add(updater);
    }

    /** Start calling the registered {@code updaters} every second */
    public void startUpdaters() {
        if (executor != null && updatersRunning.compareAndSet(false, true)) {
            executor.scheduleAtFixedRate(
                    this::runUpdaters,
                    NO_DELAY,
                    SettingsCommon.metricsUpdatePeriodMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void runUpdaters() {
        try {
            for (final Runnable updater : updaters) {
                updater.run();
            }
        } catch (final RuntimeException e) {
            exceptionRateLimiter.handle(
                    e,
                    error ->
                            LOG.error(
                                    EXCEPTION.getMarker(),
                                    "Exception while updating metrics.",
                                    error));
        }
    }

    private static String calculateMetricKey(final String category, final String name) {
        return category + "." + name;
    }

    private static String calculateMetricKey(final Metric metric) {
        return calculateMetricKey(metric.getCategory(), metric.getName());
    }

    private static String calculateMetricKey(final MetricConfig<?, ?> config) {
        return calculateMetricKey(config.getCategory(), config.getName());
    }
}

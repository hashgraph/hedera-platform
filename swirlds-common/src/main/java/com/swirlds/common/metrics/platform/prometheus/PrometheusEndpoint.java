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
package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.sun.net.httpserver.HttpServer;
import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.StatEntry;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A Prometheus endpoint that shows all {@link Metric}s. */
public class PrometheusEndpoint implements AutoCloseableNonThrowing {

    private static final Logger LOG = LogManager.getLogger(PrometheusEndpoint.class);

    /** Prometheus-label to differentiate between nodes */
    public static final String NODE_LABEL = "node";
    /** Prometheus-label to differentiate between value-types (mean, min, max, stddev) */
    public static final String TYPE_LABEL = "type";

    /** Scope of a metric */
    public enum AdapterType {
        GLOBAL,
        PLATFORM
    }

    private final CollectorRegistry registry;
    private final Map<String, MetricAdapter> adapters = new ConcurrentHashMap<>();
    private final HTTPServer httpServer;

    /**
     * Constructor of the {@code PrometheusEndpoint}
     *
     * @param httpServer The {@link HttpServer} to use for the HTTP-endpoint
     * @throws IllegalArgumentException if {@code httpServer} is {@code null}
     * @throws IOException if setting up the {@link HttpServer} fails
     */
    public PrometheusEndpoint(final HttpServer httpServer) throws IOException {
        throwArgNull(httpServer, "httpServer");
        LOG.info(
                STARTUP.getMarker(),
                "PrometheusEndpoint: Starting server listing on port: {}",
                httpServer.getAddress().getPort());

        registry = new CollectorRegistry(false);

        this.httpServer =
                new HTTPServer.Builder().withHttpServer(httpServer).withRegistry(registry).build();
    }

    /**
     * Creates a {@link MetricAdapter} for the given {@link Metric}, if it does not exist yet, and
     * registers it.
     *
     * @param metric The {@code Metric} for which the adapter should be created
     * @param adapterType Scope of the {@link Metric}, either {@link AdapterType#GLOBAL} or {@link
     *     AdapterType#PLATFORM}
     * @throws IllegalArgumentException if one of the parameters is {@code null}
     */
    public void createAdapter(final Metric metric, final AdapterType adapterType) {
        throwArgNull(metric, "metric");
        throwArgNull(adapterType, "adapterType");
        adapters.computeIfAbsent(
                DefaultMetrics.calculateMetricKey(metric), s -> doCreate(metric, adapterType));
    }

    @SuppressWarnings("removal")
    private MetricAdapter doCreate(final Metric metric, final AdapterType adapterType) {
        if (metric instanceof Counter) {
            return new CounterAdapter(registry, metric, adapterType);
        } else if (metric instanceof RunningAverageMetric || metric instanceof SpeedometerMetric) {
            return new DistributionAdapter(registry, metric, adapterType);
        } else if (metric instanceof IntegerPairAccumulator<?>
                || metric instanceof FunctionGauge<?>
                || metric instanceof StatEntry) {
            return switch (metric.getDataType()) {
                case STRING -> new StringAdapter(registry, metric, adapterType);
                case BOOLEAN -> new BooleanAdapter(registry, metric, adapterType);
                default -> new NumberAdapter(registry, metric, adapterType);
            };
        } else {
            return new NumberAdapter(registry, metric, adapterType);
        }
    }

    /**
     * Get the {@link MetricAdapter} for a given {@link Metric}.
     *
     * @param metric The {@code Metric}, which adapter is requested
     * @return The adapter if it exists, {@code null} otherwise
     * @throws IllegalArgumentException if {@code metric} is {@code null}
     */
    public MetricAdapter getAdapter(final Metric metric) {
        throwArgNull(metric, "metric");
        return adapters.get(DefaultMetrics.calculateMetricKey(metric));
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        httpServer.close();
    }
}

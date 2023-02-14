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

import static com.swirlds.common.metrics.platform.DefaultMetrics.EXCEPTION_RATE_THRESHOLD;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.GLOBAL;
import static com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType.PLATFORM;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.metrics.platform.SnapshotReceiver;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint.AdapterType;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link SnapshotReceiver} that synchronizes the provided {@link Metric}s with a Prometheus
 * endpoint
 */
public class PrometheusDataProvider implements SnapshotReceiver {

    private static final Logger LOG = LogManager.getLogger(PrometheusDataProvider.class);

    private final PrometheusEndpoint endpoint;
    private final NodeId selfId;
    private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter =
            new ThresholdLimitingHandler<>(EXCEPTION_RATE_THRESHOLD);

    /**
     * Constructor of a global {@code PrometheusDataProvider}
     *
     * @param endpoint The endpoint, that will provide the Prometheus data
     * @throws IllegalArgumentException if {@code endpoint} is {@code null}
     */
    public PrometheusDataProvider(final PrometheusEndpoint endpoint) {
        this.endpoint = throwArgNull(endpoint, "endpoint");
        this.selfId = null;
    }

    /**
     * Constructor of a platform-specific {@code PrometheusDataProvider}
     *
     * @param endpoint The endpoint, that will provide the Prometheus data
     * @param selfId The {@link NodeId} in which context the metrics are used. May be {@code null},
     *     if they are global.
     * @throws IllegalArgumentException if one of the arguments is {@code null}
     */
    public PrometheusDataProvider(final PrometheusEndpoint endpoint, final NodeId selfId) {
        this.endpoint = throwArgNull(endpoint, "endpoint");
        this.selfId = throwArgNull(selfId, "selfId");
    }

    /** {@inheritDoc} */
    @Override
    public void init(final List<Metric> metrics) {
        throwArgNull(metrics, "metrics");
        final AdapterType adapterType = selfId == null ? GLOBAL : PLATFORM;
        for (final Metric metric : metrics) {
            if (Metrics.INFO_CATEGORY.equals(metric.getCategory())
                    && "time".equals(metric.getName())) {
                // filter out the time metric, because Prometheus cannot handle often changing
                // String-values
                continue;
            }
            endpoint.createAdapter(metric, adapterType);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void handleSnapshots(final List<Snapshot> snapshots) {
        throwArgNull(snapshots, "snapshots");
        for (final Snapshot snapshot : snapshots) {
            final MetricAdapter adapter = endpoint.getAdapter(snapshot.metric());
            if (adapter != null) {
                try {
                    adapter.update(snapshot, selfId);
                } catch (RuntimeException ex) {
                    exceptionRateLimiter.handle(
                            ex,
                            error ->
                                    LOG.error(
                                            EXCEPTION.getMarker(),
                                            "Exception while trying to update Prometheus endpoint"
                                                    + " with snapshot {}",
                                            snapshot,
                                            ex));
                }
            }
        }
    }
}

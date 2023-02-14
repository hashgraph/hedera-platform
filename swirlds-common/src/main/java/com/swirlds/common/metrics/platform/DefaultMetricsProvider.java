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

import static com.swirlds.common.metrics.platform.SnapshotService.createGlobalSnapshotService;
import static com.swirlds.common.metrics.platform.SnapshotService.createPlatformSnapshotService;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.sun.net.httpserver.HttpServer;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusDataProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusEndpoint;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.time.Time;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The default implementation of {@link MetricsProvider} */
public class DefaultMetricsProvider implements MetricsProvider {

    private static final Logger LOG = LogManager.getLogger(DefaultMetricsProvider.class);

    private static final String USER_DIR = "user.dir";

    private final MetricsFactory factory = new DefaultMetricsFactory();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    getStaticThreadManager().createThreadFactory("platform-core", "MetricsThread"));
    private final Time time;

    private DefaultMetrics globalMetrics;
    private PrometheusEndpoint prometheusEndpoint;

    /**
     * Constructor of {@code DefaultMetricsProvider}
     *
     * @param time the {@link Time} to use for scheduling metrics related tasks
     */
    public DefaultMetricsProvider(final Time time) {
        this.time = time;
    }

    /** {@inheritDoc} */
    @Override
    public Metrics createGlobalMetrics() {
        if (globalMetrics != null) {
            throw new IllegalStateException("Global Metrics has already been created");
        }

        final DefaultMetrics metrics = new DefaultMetrics(executor, factory);
        final SnapshotService snapshotService =
                createGlobalSnapshotService(metrics, executor, time);
        metrics.setSnapshotService(snapshotService);
        globalMetrics = metrics;

        if (!SettingsCommon.disableMetricsOutput && SettingsCommon.prometheusEndpointEnabled) {
            final InetSocketAddress address =
                    new InetSocketAddress(SettingsCommon.prometheusEndpointPortNumber);
            try {
                final HttpServer httpServer =
                        HttpServer.create(
                                address, SettingsCommon.prometheusEndpointMaxBacklogAllowed);
                prometheusEndpoint = new PrometheusEndpoint(httpServer);

                final PrometheusDataProvider dataProvider =
                        new PrometheusDataProvider(prometheusEndpoint);
                snapshotService.addSnapshotReceiver(dataProvider);
            } catch (IOException e) {
                LOG.error("Exception while setting up Prometheus endpoint", e);
            }
        }

        return globalMetrics;
    }

    /** {@inheritDoc} */
    @Override
    public Metrics createPlatformMetrics(final NodeId selfId) {
        if (globalMetrics == null) {
            throw new IllegalStateException("Global Metrics has not been created");
        }

        final DefaultMetrics platformMetrics = new DefaultMetrics(executor, factory);
        final SnapshotService platformSnapshotService =
                createPlatformSnapshotService(platformMetrics, executor, globalMetrics, time);
        platformMetrics.setSnapshotService(platformSnapshotService);

        if (!SettingsCommon.disableMetricsOutput) {
            // setup LegacyCsvWriter
            final String folderName = SettingsCommon.csvOutputFolder;
            final Path folderPath =
                    Path.of(
                            StringUtils.isBlank(folderName)
                                    ? System.getProperty(USER_DIR)
                                    : folderName);
            if (StringUtils.isNotBlank(SettingsCommon.csvFileName)) {
                final String fileName =
                        String.format("%s%s.csv", SettingsCommon.csvFileName, selfId.getId());
                final Path csvFilePath = folderPath.resolve(fileName);

                final LegacyCsvWriter legacyCsvWriter = new LegacyCsvWriter(csvFilePath);
                platformSnapshotService.addSnapshotReceiver(legacyCsvWriter);
            }

            // setup Prometheus endpoint
            if (prometheusEndpoint != null) {
                final PrometheusDataProvider dataProvider =
                        new PrometheusDataProvider(prometheusEndpoint, selfId);
                platformSnapshotService.addSnapshotReceiver(dataProvider);
            }
        }

        return platformMetrics;
    }
}

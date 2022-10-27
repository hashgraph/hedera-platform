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

package com.swirlds.common.metrics.platform;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.NodeId;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service that orchestrates writing metrics-data to the different file formats.
 * <p>
 * This class contains only the general functionality, writing the actual files is left to the different
 * implementations of {@link MetricWriter}.
 * <p>
 * The writers are created and configured according to their settings, which are described in the javadocs
 * of each class respectively.
 * <p>
 * This class creates an {@link java.util.concurrent.ExecutorService} that triggers all writers in regular intervals.
 * The frequency of these write operations can be configured with {@link SettingsCommon#csvWriteFrequency}.
 * <p>
 * The service is not automatically started, but has to be started manually with {@link #start()}. When done,
 * the service can be shutdown with {@link #shutdown()}.
 *
 * @see MetricWriter
 * @see LegacyCsvWriter
 */
public class MetricsWriterService {

	private static final Logger LOGGER = LogManager.getLogger(MetricsWriterService.class);
	private static final long NO_DELAY = 0L;

	private final ScheduledExecutorService executor;
	private final Metrics metrics;

	private final List<MetricWriter> writers = new ArrayList<>();

	private ScheduledFuture<?> future;

	/**
	 * Constructor of the {@code MetricWriterService}.
	 * <p>
	 * The service is created, but not automatically started. It can be started with {@link #start()}.
	 *
	 * @param metrics
	 * 		a list of {@link Metric}-instances which values need to be written
	 * @param selfId
	 * 		the {@link NodeId} of the current node
	 */
	public MetricsWriterService(
			final Metrics metrics,
			final NodeId selfId,
			final ScheduledExecutorService executor) {

		this.metrics = metrics;
		this.executor = executor;

		final String folderName = SettingsCommon.csvOutputFolder;
		final Path folderPath = Path.of(
				StringUtils.isBlank(folderName) ? System.getProperty("user.dir") : folderName
		);
		if (StringUtils.isNotBlank(SettingsCommon.csvFileName)) {
			final String fileName = String.format("%s%s.csv", SettingsCommon.csvFileName, selfId);
			final Path csvFilePath = folderPath.resolve(fileName);

			writers.add(new LegacyCsvWriter(csvFilePath));
		}
	}

	/**
	 * Checks if the service is running
	 *
	 * @return {@code true}, if the service is running, {@code false} otherwise
	 */
	public boolean isRunning() {
		return !executor.isShutdown() && future != null && !future.isDone() && !future.isCancelled();
	}

	/**
	 * Starts the service
	 *
	 * @throws IllegalStateException
	 * 		if the service is running or was even shutdown already
	 */
	public void start() {
		if (future != null) {
			throw new IllegalStateException("The service was already " + (isRunning() ? "started" : "shutdown"));
		}
		final List<Metric> metricList = metrics.getAll().stream().toList();
		for (final MetricWriter writer : writers) {
			try {
				writer.prepareFile(metricList);
			} catch (IOException | RuntimeException ex) {
				LOGGER.error("Exception while trying to prepare writer {}", writer, ex);
			}
		}
		future = executor.scheduleAtFixedRate(
				this::mainLoop,
				NO_DELAY,
				SettingsCommon.csvWriteFrequency,
				TimeUnit.MILLISECONDS
		);
	}

	/**
	 * Requests the shutdown of the service and waits for its completion.
	 * <p>
	 * This method waits at most the time configured in {@link SettingsCommon#csvWriteFrequency}, which should
	 * be the maximum all writes require.
	 * <p>
	 * Calling this method after the service was already shutdown is possible and should have no effect.
	 *
	 * @return {@code true}, if the service was shutdown successfully, {@code false} if it ran into a timeout
	 * @throws IllegalStateException
	 * 		if the service was not started
	 * @throws InterruptedException
	 * 		if the method was interrupted while waiting for the shutdown
	 */
	public boolean shutdown() throws InterruptedException {
		if (future == null) {
			throw new IllegalStateException("The service was not started");
		}
		if (!isRunning()) {
			return true; // no-op
		}
		executor.shutdown();
		return executor.awaitTermination(SettingsCommon.csvWriteFrequency, TimeUnit.MILLISECONDS);
	}

	private void mainLoop() {
		final List<Snapshot> snapshots = metrics.getAll().stream()
				.map(PlatformMetric.class::cast)
				.map(Snapshot::of)
				.toList();
		for (final MetricWriter writer : writers) {
			try {
				writer.writeMetrics(snapshots);
			} catch (IOException | RuntimeException ex) {
				// Ensure that the service continues and other output is still generated
				LOGGER.error("Exception while trying to write to writer {}", writer, ex);
			}
		}
	}

}

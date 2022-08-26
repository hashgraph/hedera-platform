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

package com.swirlds.platform;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_0;
import static com.swirlds.common.statistics.internal.AbstractStatistics.INTERNAL_CATEGORY;


/**
 * Tool for tracking {@link SignedStateFileManager} statistics. This is the primary entry
 * point for all {@link SwirldMain} implementations that wish to track
 * {@link SignedStateFileManager} statistics.
 */
public final class SavedFileStatistics {

	/**
	 * list of per-file statistics created
	 */
	private final List<Pair<String, RunningAverageMetric>> trackedFiles = new ArrayList<>();

	public SavedFileStatistics() {
		init();
	}

	/**
	 * Add a file to be tracked so that when new files are written to the saved folder this one
	 * gets an explicit entry into PlatformStatsX.csv
	 *
	 * @param filename
	 * 		filename to track
	 * @param description
	 * 		description of file
	 * @return the StatEntry created for tracking this file's size
	 */
	public RunningAverageMetric createMetricForTrackingFileSize(String filename, String description) {
		final RunningAverageMetric metric = new RunningAverageMetric(
				INTERNAL_CATEGORY,
				filename,
				description,
				FORMAT_11_0,
				0
		);
		final Pair<String, RunningAverageMetric> fileSpecificData = Pair.of(filename, metric);
		trackedFiles.add(fileSpecificData);

		return metric;
	}

	private void init() {
		final NotificationEngine engine = NotificationFactory.getEngine();
		engine.register(StateWriteToDiskCompleteListener.class, notification -> {
			final File dir = notification.getFolder();
			for (final Pair<String, RunningAverageMetric> fileSpecificData : trackedFiles) {
				final File trackedFile = new File(dir, fileSpecificData.getKey());
				final long sizeInBytes = trackedFile.length();
				fileSpecificData.getValue().reset();
				fileSpecificData.getValue().recordValue(sizeInBytes);
			}
		});
	}
}

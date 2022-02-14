/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.swirlds.common.statistics.internal.AbstractStatistics.INTERNAL_CATEGORY;


/**
 * Tool for tracking {@link SignedStateFileManager} statistics. This is the primary entry
 * point for all {@link com.swirlds.common.SwirldMain} implementations that wish to track
 * {@link SignedStateFileManager} statistics.
 */
public final class SavedFileStatistics {

	/**
	 * list of per-file statistics created
	 */
	private final List<Pair<String, StatsRunningAverage>> trackedFiles = new ArrayList<>();

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
	public StatEntry createStatForTrackingFileSize(String filename, String description) {
		final StatsRunningAverage stat = new StatsRunningAverage();
		final Pair<String, StatsRunningAverage> fileSpecificData = Pair.of(filename, stat);
		trackedFiles.add(fileSpecificData);

		return new StatEntry(
				INTERNAL_CATEGORY,
				filename,
				description,
				"%,11.0f",
				stat,
				h -> {
					stat.reset(h);
					return stat;
				},
				null,
				stat::getWeightedMean
		);
	}

	private void init() {
		final NotificationEngine engine = NotificationFactory.getEngine();
		engine.register(StateWriteToDiskCompleteListener.class, notification -> {
			final File dir = notification.getFolder();
			for (final Pair<String, StatsRunningAverage> fileSpecificData : trackedFiles) {
				final File trackedFile = new File(dir, fileSpecificData.getKey());
				final long sizeInBytes = trackedFile.length();
				fileSpecificData.getValue().reset(0); //override rolling average
				fileSpecificData.getValue().recordValue(sizeInBytes);
			}
		});
	}
}

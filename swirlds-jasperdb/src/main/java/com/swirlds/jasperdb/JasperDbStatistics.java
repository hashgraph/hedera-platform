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

package com.swirlds.jasperdb;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.threading.AtomicDouble;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates statistics for an instance of a {@link VirtualDataSourceJasperDB}.
 */
public class JasperDbStatistics {

	public static final String STAT_CATEGORY = "jasper-db";
	private static final String NUMBER_OF_FILES_PREFIX = "The number of files ";
	private static final String TOTAL_FILES_SIZE_PREFIX = "The total files size (in megabytes) of files ";
	private static final String SMALL_MERGE_PREFIX = "The time (in seconds) of the last Small Merge call ";
	private static final String MEDIUM_MERGE_PREFIX = "The time (in seconds) of the last Medium Merge call ";
	private static final String LARGE_MERGE_PREFIX = "The time (in seconds) of the last Large Merge call ";
	private static final String INTERNAL_HASHES_STORE_MIDDLE = "in the internal Hashes Store for ";
	private static final String LEAF_KEY_TO_PATH_STORE_MIDDLE = "in the Leaf Key To Path Store for ";
	private static final String LEAF_PATH_TO_HKV_STORE_MIDDLE = "in the Leaf Path to Hash/Key/Value Store for ";
	private static final String SUFFIX = " for the last call to doMerge() or saveRecords().";
	private static final String MERGE_SUFFIX = " for the last call to doMerge().";
	private static final int SPEEDOMETER_HALF_LIFE_IN_SECONDS = 60;    // look at last minute

	private static final String INT_FORMAT = "%d";
	private static final String FLOAT_FORMAT = "%,9.6f";

	private final List<StatEntry> statistics;

	private StatsSpeedometer internalNodeWritesPerSecond;
	private StatsSpeedometer internalNodeReadsPerSecond;
	private StatsSpeedometer leafWritesPerSecond;
	private StatsSpeedometer leafByKeyReadsPerSecond;
	private StatsSpeedometer leafByPathReadsPerSecond;

	private final AtomicInteger internalHashesStoreFileCount;
	private final AtomicDouble internalHashesStoreTotalFileSizeInMB;

	private final AtomicInteger leafKeyToPathStoreFileCount;
	private final AtomicDouble leafKeyToPathStoreTotalFileSizeInMB;

	private final AtomicInteger leafPathToHashKeyValueStoreFileCount;
	private final AtomicDouble leafPathToHashKeyValueStoreTotalFileSizeInMB;

	private final AtomicDouble internalHashesStoreSmallMergeTime;
	private final AtomicDouble internalHashesStoreMediumMergeTime;
	private final AtomicDouble internalHashesStoreLargeMergeTime;
	private final AtomicDouble leafKeyToPathStoreSmallMergeTime;
	private final AtomicDouble leafKeyToPathStoreMediumMergeTime;
	private final AtomicDouble leafKeyToPathStoreLargeMergeTime;
	private final AtomicDouble leafPathToHashKeyValueStoreSmallMergeTime;
	private final AtomicDouble leafPathToHashKeyValueStoreMediumMergeTime;
	private final AtomicDouble leafPathToHashKeyValueStoreLargeMergeTime;

	/**
	 * Create a new statistics object for a JPDB instances.
	 *
	 * @param label
	 * 		the label for the virtual map
	 * @param isLongKeyMode
	 * 		true if the long key optimization is enabled
	 */
	public JasperDbStatistics(final String label, final boolean isLongKeyMode) {
		statistics = new LinkedList<>();

		internalNodeWritesPerSecond = new StatsSpeedometer(SPEEDOMETER_HALF_LIFE_IN_SECONDS);
		internalNodeReadsPerSecond = new StatsSpeedometer(SPEEDOMETER_HALF_LIFE_IN_SECONDS);
		leafWritesPerSecond = new StatsSpeedometer(SPEEDOMETER_HALF_LIFE_IN_SECONDS);
		leafByKeyReadsPerSecond = new StatsSpeedometer(SPEEDOMETER_HALF_LIFE_IN_SECONDS);
		leafByPathReadsPerSecond = new StatsSpeedometer(SPEEDOMETER_HALF_LIFE_IN_SECONDS);

		internalHashesStoreFileCount = new AtomicInteger();
		internalHashesStoreTotalFileSizeInMB = new AtomicDouble();
		leafKeyToPathStoreFileCount = new AtomicInteger();
		leafKeyToPathStoreTotalFileSizeInMB = new AtomicDouble();
		leafPathToHashKeyValueStoreFileCount = new AtomicInteger();
		leafPathToHashKeyValueStoreTotalFileSizeInMB = new AtomicDouble();
		internalHashesStoreSmallMergeTime = new AtomicDouble();
		internalHashesStoreMediumMergeTime = new AtomicDouble();
		internalHashesStoreLargeMergeTime = new AtomicDouble();
		leafKeyToPathStoreSmallMergeTime = new AtomicDouble();
		leafKeyToPathStoreMediumMergeTime = new AtomicDouble();
		leafKeyToPathStoreLargeMergeTime = new AtomicDouble();
		leafPathToHashKeyValueStoreSmallMergeTime = new AtomicDouble();
		leafPathToHashKeyValueStoreMediumMergeTime = new AtomicDouble();
		leafPathToHashKeyValueStoreLargeMergeTime = new AtomicDouble();

		buildStatistics(label, isLongKeyMode);
	}

	/**
	 * This method fills in some default arguments for the StatEntry constructor and adds the statistic to the
	 * list of all statistics.
	 */
	private void buildStatistic(
			final String name,
			final String description,
			final String format,
			final Supplier<Object> getStat) {

		buildStatistic(name, description, format, null, null, getStat);
	}

	/**
	 * This method fills in some default arguments for the StatEntry constructor and adds the statistic to the
	 * list of all statistics.
	 */
	private void buildStatistic(
			final String name,
			final String description,
			final String format,
			final StatsBuffered buffered,
			final Function<Double, StatsBuffered> init,
			final Supplier<Object> getStat) {

		statistics.add(new StatEntry(STAT_CATEGORY, name, description, format, buffered, init, null, getStat));
	}

	private void buildStatistics(final String label, final boolean isLongKeyMode) {
		buildStatistic("internalNodeWrites/s_" + label,
				"number of internal node writes per second for " + label,
				FLOAT_FORMAT,
				internalNodeWritesPerSecond,
				h -> {
					internalNodeWritesPerSecond = new StatsSpeedometer(h);
					return internalNodeWritesPerSecond;
				},
				() -> internalNodeWritesPerSecond.getCyclesPerSecond());

		buildStatistic("internalNodeReads/s_" + label,
				"number of internal node reads per second for " + label,
				FLOAT_FORMAT,
				internalNodeReadsPerSecond,
				h -> {
					internalNodeReadsPerSecond = new StatsSpeedometer(h);
					return internalNodeReadsPerSecond;
				},
				() -> internalNodeReadsPerSecond.getCyclesPerSecond());

		buildStatistic("leafWrites/s_" + label,
				"number of leaf writes per second for " + label,
				FLOAT_FORMAT,
				leafWritesPerSecond,
				h -> {
					leafWritesPerSecond = new StatsSpeedometer(h);
					return leafWritesPerSecond;
				},
				() -> leafWritesPerSecond.getCyclesPerSecond());

		buildStatistic("leafByKeyReads/s_" + label,
				"number of leaf by key reads per second for " + label,
				FLOAT_FORMAT,
				leafByKeyReadsPerSecond,
				h -> {
					leafByKeyReadsPerSecond = new StatsSpeedometer(h);
					return leafByKeyReadsPerSecond;
				},
				() -> leafByKeyReadsPerSecond.getCyclesPerSecond());

		buildStatistic("leafByPathReads/s_" + label,
				"number of leaf by path reads per second for " + label,
				FLOAT_FORMAT,
				leafByPathReadsPerSecond,
				h -> {
					leafByPathReadsPerSecond = new StatsSpeedometer(h);
					return leafByPathReadsPerSecond;
				},
				() -> leafByPathReadsPerSecond.getCyclesPerSecond());

		buildStatistic("internalHashFileCount_" + label,
				NUMBER_OF_FILES_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX,
				INT_FORMAT,
				internalHashesStoreFileCount::get);

		buildStatistic("internalHashFileSizeMb_" + label,
				TOTAL_FILES_SIZE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX,
				FLOAT_FORMAT,
				internalHashesStoreTotalFileSizeInMB::get);

		if (!isLongKeyMode) {
			buildStatistic("leafKeyToPathFileCount_" + label,
					NUMBER_OF_FILES_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX,
					INT_FORMAT,
					leafKeyToPathStoreFileCount::get);

			buildStatistic("leafKeyToPathFileSizeMb_" + label,
					TOTAL_FILES_SIZE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX,
					FLOAT_FORMAT,
					leafKeyToPathStoreTotalFileSizeInMB::get);
		}

		buildStatistic("leafHKVFileCount_" + label,
				NUMBER_OF_FILES_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX,
				INT_FORMAT,
				leafPathToHashKeyValueStoreFileCount::get);

		buildStatistic("leafHKVFileSizeMb_" + label,
				TOTAL_FILES_SIZE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX,
				FLOAT_FORMAT,
				leafPathToHashKeyValueStoreTotalFileSizeInMB::get);

		buildStatistic("internalHashSmallMergeTime_" + label,
				SMALL_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				internalHashesStoreSmallMergeTime::get);

		buildStatistic("internalHashMediumMergeTime_" + label,
				MEDIUM_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				internalHashesStoreMediumMergeTime::get);

		buildStatistic("internalHashLargeMergeTime_" + label,
				LARGE_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				internalHashesStoreLargeMergeTime::get);

		if (!isLongKeyMode) {
			buildStatistic("leafKeyToPathSmallMergeTime_" + label,
					SMALL_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX,
					FLOAT_FORMAT,
					leafKeyToPathStoreSmallMergeTime::get);

			buildStatistic("leafKeyToPathMediumMergeTime_" + label,
					MEDIUM_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX,
					FLOAT_FORMAT,
					leafKeyToPathStoreMediumMergeTime::get);

			buildStatistic("leafKeyToPathLargeMergeTime_" + label,
					LARGE_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX,
					FLOAT_FORMAT,
					leafKeyToPathStoreLargeMergeTime::get);
		}

		buildStatistic("leafHKVSmallMergeTime_" + label,
				SMALL_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				leafPathToHashKeyValueStoreSmallMergeTime::get);

		buildStatistic("leafHKVMediumMergeTime_" + label,
				MEDIUM_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				leafPathToHashKeyValueStoreMediumMergeTime::get);

		buildStatistic("leafHKVLargeMergeTime_" + label,
				LARGE_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX,
				FLOAT_FORMAT,
				leafPathToHashKeyValueStoreLargeMergeTime::get);
	}

	/**
	 * Register all statistics with a registry.
	 *
	 * @param registry
	 * 		an object that manages statistics
	 */
	public void registerStatistics(final Consumer<StatEntry> registry) {
		for (final StatEntry statEntry : statistics) {
			registry.accept(statEntry);
		}
	}

	/**
	 * Cycle the InternalNodeWritesPerSecond stat
	 */
	public void cycleInternalNodeWritesPerSecond() {
		internalNodeWritesPerSecond.cycle();
	}

	/**
	 * Cycle the InternalNodeReadsPerSecond stat
	 */
	public void cycleInternalNodeReadsPerSecond() {
		internalNodeReadsPerSecond.cycle();
	}

	/**
	 * Cycle the LeafWritesPerSecond stat
	 */
	public void cycleLeafWritesPerSecond() {
		leafWritesPerSecond.cycle();
	}

	/**
	 * Cycle the LeafByKeyReadsPerSecond stat
	 */
	public void cycleLeafByKeyReadsPerSecond() {
		leafByKeyReadsPerSecond.cycle();
	}

	/**
	 * Cycle the LeafByPathReadsPerSecond stat
	 */
	public void cycleLeafByPathReadsPerSecond() {
		leafByPathReadsPerSecond.cycle();
	}

	/**
	 * Set the current value for the InternalHashesStoreFileCount stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setInternalHashesStoreFileCount(final int value) {
		internalHashesStoreFileCount.set(value);
	}

	/**
	 * Set the current value for the InternalHashesStoreTotalFileSizeInMB stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setInternalHashesStoreTotalFileSizeInMB(final double value) {
		internalHashesStoreTotalFileSizeInMB.set(value);
	}

	/**
	 * Set the current value for the LeafKeyToPathStoreFileCount stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafKeyToPathStoreFileCount(final int value) {
		leafKeyToPathStoreFileCount.set(value);
	}

	/**
	 * Set the current value for the LeafKeyToPathStoreTotalFileSizeInMB stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafKeyToPathStoreTotalFileSizeInMB(final double value) {
		leafKeyToPathStoreTotalFileSizeInMB.set(value);
	}

	/**
	 * Set the current value for the LeafPathToHashKeyValueStoreFileCount stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafPathToHashKeyValueStoreFileCount(final int value) {
		leafPathToHashKeyValueStoreFileCount.set(value);
	}

	/**
	 * Set the current value for the LeafPathToHashKeyValueStoreTotalFileSizeInMB stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafPathToHashKeyValueStoreTotalFileSizeInMB(final double value) {
		leafPathToHashKeyValueStoreTotalFileSizeInMB.set(value);
	}

	/**
	 * Set the current value for the InternalHashesStoreSmallMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setInternalHashesStoreSmallMergeTime(final double value) {
		internalHashesStoreSmallMergeTime.set(value);
	}

	/**
	 * Set the current value for the InternalHashesStoreMediumMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setInternalHashesStoreMediumMergeTime(final double value) {
		internalHashesStoreMediumMergeTime.set(value);
	}

	/**
	 * Set the current value for the InternalHashesStoreLargeMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setInternalHashesStoreLargeMergeTime(final double value) {
		internalHashesStoreLargeMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafKeyToPathStoreSmallMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafKeyToPathStoreSmallMergeTime(final double value) {
		leafKeyToPathStoreSmallMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafKeyToPathStoreMediumMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafKeyToPathStoreMediumMergeTime(final double value) {
		leafKeyToPathStoreMediumMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafKeyToPathStoreLargeMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafKeyToPathStoreLargeMergeTime(final double value) {
		leafKeyToPathStoreLargeMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafPathToHashKeyValueStoreSmallMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafPathToHashKeyValueStoreSmallMergeTime(final double value) {
		leafPathToHashKeyValueStoreSmallMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafPathToHashKeyValueStoreMediumMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafPathToHashKeyValueStoreMediumMergeTime(final double value) {
		leafPathToHashKeyValueStoreMediumMergeTime.set(value);
	}

	/**
	 * Set the current value for the LeafPathToHashKeyValueStoreLargeMergeTime stat
	 *
	 * @param value
	 * 		the value to set
	 */
	public void setLeafPathToHashKeyValueStoreLargeMergeTime(final double value) {
		leafPathToHashKeyValueStoreLargeMergeTime.set(value);
	}
}

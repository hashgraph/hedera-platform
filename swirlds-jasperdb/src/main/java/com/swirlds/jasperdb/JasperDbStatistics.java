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

import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.IntegerGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.SpeedometerMetric;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

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

	private final List<Metric> statistics;

	private final SpeedometerMetric internalNodeWritesPerSecond;
	private final SpeedometerMetric internalNodeReadsPerSecond;
	private final SpeedometerMetric leafWritesPerSecond;
	private final SpeedometerMetric leafByKeyReadsPerSecond;
	private final SpeedometerMetric leafByPathReadsPerSecond;

	private final IntegerGauge internalHashesStoreFileCount;
	private final DoubleGauge internalHashesStoreTotalFileSizeInMB;

	private final IntegerGauge leafKeyToPathStoreFileCount;

	private final DoubleGauge leafKeyToPathStoreTotalFileSizeInMB;

	private final IntegerGauge leafPathToHashKeyValueStoreFileCount;

	private final DoubleGauge leafPathToHashKeyValueStoreTotalFileSizeInMB;

	private final DoubleGauge internalHashesStoreSmallMergeTime;

	private final DoubleGauge internalHashesStoreMediumMergeTime;
	private final DoubleGauge internalHashesStoreLargeMergeTime;
	private final DoubleGauge leafKeyToPathStoreSmallMergeTime;
	private final DoubleGauge leafKeyToPathStoreMediumMergeTime;
	private final DoubleGauge leafKeyToPathStoreLargeMergeTime;
	private final DoubleGauge leafPathToHashKeyValueStoreSmallMergeTime;
	private final DoubleGauge leafPathToHashKeyValueStoreMediumMergeTime;
	private final DoubleGauge leafPathToHashKeyValueStoreLargeMergeTime;

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

		internalNodeWritesPerSecond = buildSpeedometerMetric(
				"internalNodeWrites/s_" + label,
				"number of internal node writes per second for " + label
		);
		internalNodeReadsPerSecond = buildSpeedometerMetric(
				"internalNodeReads/s_" + label,
				"number of internal node reads per second for " + label
		);
		leafWritesPerSecond = buildSpeedometerMetric(
				"leafWrites/s_" + label,
				"number of leaf writes per second for " + label
		);
		leafByKeyReadsPerSecond = buildSpeedometerMetric(
				"leafByKeyReads/s_" + label,
				"number of leaf by key reads per second for " + label
		);
		leafByPathReadsPerSecond = buildSpeedometerMetric(
				"leafByPathReads/s_" + label,
				"number of leaf by path reads per second for " + label
		);
		internalHashesStoreFileCount = new IntegerGauge(
				STAT_CATEGORY,
				"internalHashFileCount_" + label,
				NUMBER_OF_FILES_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX
		);
		internalHashesStoreTotalFileSizeInMB = buildDoubleGauge(
				"internalHashFileSizeMb_" + label,
				TOTAL_FILES_SIZE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + SUFFIX
		);
		leafKeyToPathStoreFileCount = isLongKeyMode? null : new IntegerGauge(
				STAT_CATEGORY,
				"leafKeyToPathFileCount_" + label,
				NUMBER_OF_FILES_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX
		);
		leafKeyToPathStoreTotalFileSizeInMB = isLongKeyMode? null : buildDoubleGauge(
				"leafKeyToPathFileSizeMb_" + label,
				TOTAL_FILES_SIZE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + SUFFIX
		);
		leafPathToHashKeyValueStoreFileCount = new IntegerGauge(
				STAT_CATEGORY,
				"leafHKVFileCount_" + label,
				NUMBER_OF_FILES_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX
		);
		leafPathToHashKeyValueStoreTotalFileSizeInMB = buildDoubleGauge(
				"leafHKVFileSizeMb_" + label,
				TOTAL_FILES_SIZE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + SUFFIX
		);
		internalHashesStoreSmallMergeTime = buildDoubleGauge(
				"internalHashSmallMergeTime_" + label,
				SMALL_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		internalHashesStoreMediumMergeTime = buildDoubleGauge(
				"internalHashMediumMergeTime_" + label,
				MEDIUM_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		internalHashesStoreLargeMergeTime = buildDoubleGauge(
				"internalHashLargeMergeTime_" + label,
				LARGE_MERGE_PREFIX + INTERNAL_HASHES_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafKeyToPathStoreSmallMergeTime = isLongKeyMode? null : buildDoubleGauge(
				"leafKeyToPathSmallMergeTime_" + label,
				SMALL_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafKeyToPathStoreMediumMergeTime = isLongKeyMode? null : buildDoubleGauge(
				"leafKeyToPathMediumMergeTime_" + label,
				MEDIUM_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafKeyToPathStoreLargeMergeTime = isLongKeyMode? null : buildDoubleGauge(
				"leafKeyToPathLargeMergeTime_" + label,
				LARGE_MERGE_PREFIX + LEAF_KEY_TO_PATH_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafPathToHashKeyValueStoreSmallMergeTime = buildDoubleGauge(
				"leafHKVSmallMergeTime_" + label,
				SMALL_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafPathToHashKeyValueStoreMediumMergeTime = buildDoubleGauge(
				"leafHKVMediumMergeTime_" + label,
				MEDIUM_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX
		);
		leafPathToHashKeyValueStoreLargeMergeTime = buildDoubleGauge(
				"leafHKVLargeMergeTime_" + label,
				LARGE_MERGE_PREFIX + LEAF_PATH_TO_HKV_STORE_MIDDLE + label + MERGE_SUFFIX
		);

		buildStatistics(isLongKeyMode);
	}

	private static SpeedometerMetric buildSpeedometerMetric(final String name, final String description) {
		return new SpeedometerMetric(STAT_CATEGORY, name, description, FORMAT_9_6, SPEEDOMETER_HALF_LIFE_IN_SECONDS);
	}

	private static DoubleGauge buildDoubleGauge(final String name, final String description) {
		return new DoubleGauge(STAT_CATEGORY, name, description, FORMAT_9_6);
	}

	private void buildStatistics(final boolean isLongKeyMode) {
		statistics.addAll(List.of(
				internalNodeWritesPerSecond,
				internalNodeReadsPerSecond,
				leafWritesPerSecond,
				leafByKeyReadsPerSecond,
				leafByPathReadsPerSecond,
				internalHashesStoreFileCount,
				internalHashesStoreTotalFileSizeInMB
		));

		if (!isLongKeyMode) {
			statistics.addAll(List.of(
					leafKeyToPathStoreFileCount,
					leafKeyToPathStoreTotalFileSizeInMB
			));
		}

		statistics.addAll(List.of(
				leafPathToHashKeyValueStoreFileCount,
				leafPathToHashKeyValueStoreTotalFileSizeInMB,
				internalHashesStoreSmallMergeTime,
				internalHashesStoreMediumMergeTime,
				internalHashesStoreLargeMergeTime
		));

		if (!isLongKeyMode) {
			statistics.addAll(List.of(
					leafKeyToPathStoreSmallMergeTime,
					leafKeyToPathStoreMediumMergeTime,
					leafKeyToPathStoreLargeMergeTime
			));
		}

		statistics.addAll(List.of(
				leafPathToHashKeyValueStoreSmallMergeTime,
				leafPathToHashKeyValueStoreMediumMergeTime,
				leafPathToHashKeyValueStoreLargeMergeTime
		));
	}

	/**
	 * Register all statistics with a registry.
	 *
	 * @param registry
	 * 		an object that manages statistics
	 */
	public void registerStatistics(final Consumer<Metric> registry) {
		for (final Metric metric : statistics) {
			registry.accept(metric);
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

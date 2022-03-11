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

package com.swirlds.jasperdb.settings;

import com.swirlds.jasperdb.collections.ThreeLongsList;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;

/**
 * {@link JasperDbSettings} implementation with defaults appropriate for JUnit tests.
 *
 * Necessary for testing {@link JasperDbSettingsFactory} client code running in an environment
 * without Browser-configured settings.
 */
public class DefaultJasperDbSettings implements JasperDbSettings {
	/** The default storage directory to create databases in */
	public static final String DEFAULT_STORAGE_DIRECTORY = Path.of("database").toAbsolutePath().toString();
	/** A default of 500 million should be big enough to allow us a few billion before having to think about it */
	public static final int DEFAULT_MAX_NUM_OF_KEYS = 500_000_000;
	/** Default to 100% on disk */
	public static final int DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD = 0;
	public static final int DEFAULT_MAX_GB_RAM_FOR_MERGING = 10;
	public static final int DEFAULT_SMALL_MERGE_CUTOFF_MB = 3 * 1024;
	public static final int DEFAULT_MEDIUM_MERGE_CUTOFF_MB = 10 * 1024;
	public static final int DEFAULT_ITERATOR_INPUT_BUFFER_BYTES = 1024 * 1024;
	public static final int DEFAULT_WRITER_OUTPUT_BUFFER_BYTES = 4 * 1024 * 1024;
	public static final int DEFAULT_MOVE_LIST_CHUNK_SIZE = 500_000;
	public static final int DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE = 64;
	public static final int DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE = 8;
	public static final long DEFAULT_MERGE_ACTIVATED_PERIOD = 1L; // 1 seconds
	public static final long DEFAULT_MEDIUM_MERGE_PERIOD = 60L; // 1h
	public static final long DEFAULT_FULL_MERGE_PERIOD = 1440L; // 24h in min
	public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 64L * 1024 * 1024 * 1024;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getStoragePath() {
		return DEFAULT_STORAGE_DIRECTORY;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMaxNumOfKeys() {
		return DEFAULT_MAX_NUM_OF_KEYS;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getInternalHashesRamToDiskThreshold() {
		return DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMediumMergeCutoffMb() {
		return DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSmallMergeCutoffMb() {
		return DEFAULT_SMALL_MERGE_CUTOFF_MB;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ChronoUnit getMergePeriodUnit() {
		return ChronoUnit.MINUTES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxNumberOfFilesInMerge() {
		return DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinNumberOfFilesInMerge() {
		return DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMergeActivatePeriod() {
		return DEFAULT_MERGE_ACTIVATED_PERIOD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMediumMergePeriod() {
		return DEFAULT_MEDIUM_MERGE_PERIOD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getFullMergePeriod() {
		return DEFAULT_FULL_MERGE_PERIOD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMaxDataFileBytes() {
		return DEFAULT_MAX_FILE_SIZE_BYTES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMoveListChunkSize() {
		return ThreeLongsList.SMALL_TRIPLES_PER_CHUNK;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxRamUsedForMergingGb() {
		return DEFAULT_MAX_GB_RAM_FOR_MERGING;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getIteratorInputBufferBytes() {
		return DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getWriterOutputBufferBytes() {
		return DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;
	}
}

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

import com.swirlds.jasperdb.settings.JasperDbSettings;
import com.swirlds.platform.internal.SubSetting;

import java.time.temporal.ChronoUnit;

import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_FULL_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_FILE_SIZE_BYTES;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_GB_RAM_FOR_MERGING;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUM_OF_KEYS;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MEDIUM_MERGE_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MERGE_ACTIVATED_PERIOD;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MOVE_LIST_CHUNK_SIZE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_SMALL_MERGE_CUTOFF_MB;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_STORAGE_DIRECTORY;
import static com.swirlds.jasperdb.settings.DefaultJasperDbSettings.DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;

@SuppressWarnings("unused")
public class JasperDbSettingsImpl extends SubSetting implements JasperDbSettings {
	public static final int MAX_NUMBER_OF_SAVES_BEFORE_MERGE = 100;

	public String storagePath = DEFAULT_STORAGE_DIRECTORY;
	public int maxNumOfKeys = DEFAULT_MAX_NUM_OF_KEYS;
	public int internalHashesRamToDiskThreshold = DEFAULT_INTERNAL_HASHES_RAM_TO_DISK_THRESHOLD;
	public int smallMergeCutoffMb = DEFAULT_SMALL_MERGE_CUTOFF_MB;
	public int mediumMergeCutoffMb = DEFAULT_MEDIUM_MERGE_CUTOFF_MB;
	public int moveListChunkSize = DEFAULT_MOVE_LIST_CHUNK_SIZE;
	public int maxRamUsedForMergingGb = DEFAULT_MAX_GB_RAM_FOR_MERGING;
	public int iteratorInputBufferBytes = DEFAULT_ITERATOR_INPUT_BUFFER_BYTES;
	public int writerOutputBufferBytes = DEFAULT_WRITER_OUTPUT_BUFFER_BYTES;
	public long maxDataFileBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
	public long fullMergePeriod = DEFAULT_FULL_MERGE_PERIOD;
	public long mediumMergePeriod = DEFAULT_MEDIUM_MERGE_PERIOD;
	public long mergeActivatedPeriod = DEFAULT_MERGE_ACTIVATED_PERIOD;
	public int maxNumberOfFilesInMerge = DEFAULT_MAX_NUMBER_OF_FILES_IN_MERGE;
	public int minNumberOfFilesInMerge = DEFAULT_MIN_NUMBER_OF_FILES_IN_MERGE;
	public String mergePeriodUnit = "MINUTES";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getStoragePath() {
		return this.storagePath;
	}

	public void setStoragePath(final String storagePath) {
		this.storagePath = storagePath;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMaxNumOfKeys() {
		return maxNumOfKeys;
	}

	public void setMaxNumOfKeys(final int maxNumOfKeys) {
		if (maxNumOfKeys <= 0) {
			throw new IllegalArgumentException("Cannot configure maxNumOfKeys=" + maxNumOfKeys);
		}
		this.maxNumOfKeys = maxNumOfKeys;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getInternalHashesRamToDiskThreshold() {
		return internalHashesRamToDiskThreshold;
	}

	public void setInternalHashesRamToDiskThreshold(final int internalHashesRamToDiskThreshold) {
		if (internalHashesRamToDiskThreshold < 0) {
			throw new IllegalArgumentException("Cannot configure internalHashesRamToDiskThreshold=" +
					internalHashesRamToDiskThreshold);
		}
		this.internalHashesRamToDiskThreshold = internalHashesRamToDiskThreshold;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMediumMergeCutoffMb() {
		return mediumMergeCutoffMb;
	}

	public void setMediumMergeCutoffMb(final int mediumMergeCutoffMb) {
		this.mediumMergeCutoffMb = mediumMergeCutoffMb;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSmallMergeCutoffMb() {
		return smallMergeCutoffMb;
	}

	public void setSmallMergeCutoffMb(int smallMergeCutoffMb) {
		this.smallMergeCutoffMb = smallMergeCutoffMb;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ChronoUnit getMergePeriodUnit() {
		return ChronoUnit.valueOf(mergePeriodUnit);
	}

	public void setMergePeriodUnit(String mergePeriodUnit) {
		ChronoUnit.valueOf(mergePeriodUnit);
		this.mergePeriodUnit = mergePeriodUnit;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getFullMergePeriod() {
		return fullMergePeriod;
	}

	public void setFullMergePeriod(final long fullMergePeriod) {
		if (fullMergePeriod < 0) {
			throw new IllegalArgumentException("Cannot configure fullMergePeriod=" + fullMergePeriod);
		}
		this.fullMergePeriod = fullMergePeriod;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMediumMergePeriod() {
		return mediumMergePeriod;
	}

	public void setMediumMergePeriod(final long mediumMergePeriod) {
		if (mediumMergePeriod < 0) {
			throw new IllegalArgumentException("Cannot configure mediumMergePeriod=" + mediumMergePeriod);
		}
		this.mediumMergePeriod = mediumMergePeriod;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMergeActivatePeriod() {
		return mergeActivatedPeriod;
	}

	public void setMergeActivatedPeriod(final long mergeActivatedPeriod) {
		if (mergeActivatedPeriod < 0) {
			throw new IllegalArgumentException("Cannot configure smallMergePeriod=" + mergeActivatedPeriod);
		}
		this.mergeActivatedPeriod = mergeActivatedPeriod;
	}

	/**
	 * {@inheritDoc}
	 * @return
	 */
	@Override
	public int getMaxNumberOfFilesInMerge() {
		return maxNumberOfFilesInMerge;
	}

	public void setMaxNumberOfFilesInMerge(final int maxNumberOfFilesInMerge) {
		if (maxNumberOfFilesInMerge <= minNumberOfFilesInMerge) {
			throw new IllegalArgumentException(
					"Cannot configure maxNumberOfFilesInMerge to " + maxNumberOfFilesInMerge +
							", it mist be > " + minNumberOfFilesInMerge);
		}
		this.maxNumberOfFilesInMerge = maxNumberOfFilesInMerge;
	}

	/**
	 * {@inheritDoc}
	 * @return
	 */
	@Override
	public int getMinNumberOfFilesInMerge() {
		return minNumberOfFilesInMerge;
	}

	public void setMinNumberOfFilesInMerge(final int minNumberOfFilesInMerge) {
		if (minNumberOfFilesInMerge < 2 || minNumberOfFilesInMerge >= maxNumberOfFilesInMerge) {
			throw new IllegalArgumentException(
					"Cannot configure minNumberOfFilesInMerge to " + minNumberOfFilesInMerge +
							", it must be >= 2 and < "+maxNumberOfFilesInMerge);
		}
		this.minNumberOfFilesInMerge = minNumberOfFilesInMerge;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getMaxDataFileBytes() {
		return maxDataFileBytes;
	}

	public void setMaxDataFileBytes(final long maxDataFileBytes) {
		if (maxDataFileBytes < 0) {
			throw new IllegalArgumentException("Cannot configure maxDataFileBytes=" + maxDataFileBytes);
		}
		this.maxDataFileBytes = maxDataFileBytes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMoveListChunkSize() {
		return moveListChunkSize;
	}

	public void setMoveListChunkSize(final int moveListChunkSize) {
		if (moveListChunkSize <= 0) {
			throw new IllegalArgumentException("Cannot configure moveListChunkSize=" + moveListChunkSize);
		}
		this.moveListChunkSize = moveListChunkSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaxRamUsedForMergingGb() {
		return maxRamUsedForMergingGb;
	}

	public void setMaxRamUsedForMergingGb(final int maxRamUsedForMergingGb) {
		if (maxRamUsedForMergingGb < 0) {
			throw new IllegalArgumentException("Cannot configure maxRamUsedForMergingGb=" + maxRamUsedForMergingGb);
		}
		this.maxRamUsedForMergingGb = maxRamUsedForMergingGb;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getIteratorInputBufferBytes() {
		return iteratorInputBufferBytes;
	}

	public void setIteratorInputBufferBytes(final int iteratorInputBufferBytes) {
		if (iteratorInputBufferBytes <= 0) {
			throw new IllegalArgumentException("Cannot configure iteratorInputBufferBytes=" + iteratorInputBufferBytes);
		}
		this.iteratorInputBufferBytes = iteratorInputBufferBytes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getWriterOutputBufferBytes() {
		return writerOutputBufferBytes;
	}

	public void setWriterOutputBufferBytes(final int writerOutputBufferBytes) {
		if (writerOutputBufferBytes <= 0) {
			throw new IllegalArgumentException("Cannot configure writerOutputBufferBytes=" + writerOutputBufferBytes);
		}
		this.writerOutputBufferBytes = writerOutputBufferBytes;
	}
}


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

import com.swirlds.fchashmap.FCHashMapSettings;
import com.swirlds.platform.internal.SubSetting;

import java.time.Duration;

/**
 * An implementation of {@link FCHashMapSettings}.
 */
public class FCHashMapSettingsImpl extends SubSetting implements FCHashMapSettings {

	public int maximumGCQueueSize = 200;
	public Duration gcQueueThresholdPeriod = Duration.ofMinutes(1);
	public boolean archiveEnabled = true;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaximumGCQueueSize() {
		return maximumGCQueueSize;
	}

	public void setMaximumGCQueueSize(final int maximumGCQueueSize) {
		this.maximumGCQueueSize = maximumGCQueueSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getGCQueueThresholdPeriod() {
		return gcQueueThresholdPeriod;
	}

	public void setGCQueueThresholdPeriod(final Duration gcQueueThresholdPeriod) {
		this.gcQueueThresholdPeriod = gcQueueThresholdPeriod;
	}

	/**
	 * Check if archival of the FCHashMap is enabled.
	 */
	@Override
	public boolean isArchiveEnabled() {
		return archiveEnabled;
	}

	/**
	 * Set if archival of the FCHashMap is enabled.
	 */
	public void setArchiveEnabled(final boolean archiveEnabled) {
		this.archiveEnabled = archiveEnabled;
	}
}

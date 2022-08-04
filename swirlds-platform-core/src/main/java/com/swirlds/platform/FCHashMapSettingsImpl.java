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

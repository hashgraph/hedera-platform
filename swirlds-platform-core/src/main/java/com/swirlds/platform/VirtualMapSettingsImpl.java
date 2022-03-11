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

import com.swirlds.platform.internal.SubSetting;
import com.swirlds.virtualmap.VirtualMapSettings;

import java.time.Duration;

import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_FLUSH_INTERVAL;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_FLUSH_THROTTLE_STEP_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_NUM_CLEANER_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_NUM_HASH_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PERCENT_CLEANER_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PERCENT_HASH_THREADS;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL;
import static com.swirlds.virtualmap.DefaultVirtualMapSettings.DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD;

public class VirtualMapSettingsImpl extends SubSetting implements VirtualMapSettings {
	/**
	 * If not set explicitly via {@code virtualMap.numHashThreads}, the number of
	 * hash threads defaults to a calculation based on {@link VirtualMapSettings#getPercentHashThreads()}
	 * and  {@link Runtime#availableProcessors()}.
	 */
	public int numHashThreads = DEFAULT_NUM_HASH_THREADS;
	public double percentHashThreads = DEFAULT_PERCENT_HASH_THREADS;
	public int numCleanerThreads = DEFAULT_NUM_CLEANER_THREADS;
	public double percentCleanerThreads = DEFAULT_PERCENT_CLEANER_THREADS;
	public long maximumVirtualMapSize = DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE;
	public long virtualMapWarningThreshold = DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD;
	public long virtualMapWarningInterval = DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL;
	public int flushInterval = DEFAULT_FLUSH_INTERVAL;
	public int preferredFlushQueueSize = DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE;
	public Duration flushThrottleStepSize = DEFAULT_FLUSH_THROTTLE_STEP_SIZE;
	public Duration maximumFlushThrottlePeriod = DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getPercentHashThreads() {
		return percentHashThreads;
	}

	public void setPercentHashThreads(final double percentHashThreads) {
		if (percentHashThreads < 0.0 || percentHashThreads > UNIT_FRACTION_PERCENT) {
			throw new IllegalArgumentException("Cannot configure percentHashThreads=" + percentHashThreads);
		}
		this.percentHashThreads = percentHashThreads;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumHashThreads() {
		return (numHashThreads == -1)
				? (int) (Runtime.getRuntime().availableProcessors() * (getPercentHashThreads() / UNIT_FRACTION_PERCENT))
				: numHashThreads;
	}

	public void setNumHashThreads(final int numHashThreads) {
		if (numHashThreads < 0) {
			throw new IllegalArgumentException("Cannot configure numHashThreads=" + numHashThreads);
		}
		this.numHashThreads = numHashThreads;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getPercentCleanerThreads() {
		return percentCleanerThreads;
	}

	public void setPercentCleanerThreads(final double percentCleanerThreads) {
		if (percentCleanerThreads < 0.0 || percentCleanerThreads > UNIT_FRACTION_PERCENT) {
			throw new IllegalArgumentException("Cannot configure percentCleanerThreads=" + percentCleanerThreads);
		}
		this.percentCleanerThreads = percentCleanerThreads;
	}

	@Override
	public int getNumCleanerThreads() {
		final int numProcessors = Runtime.getRuntime().availableProcessors();
		return (numCleanerThreads == -1)
				? (int) (numProcessors * (getPercentCleanerThreads() / UNIT_FRACTION_PERCENT))
				: numCleanerThreads;
	}

	public void setNumCleanerThreads(final int numCleanerThreads) {
		if (numCleanerThreads < 0) {
			throw new IllegalArgumentException("Cannot configure numCleanerThreads=" + numCleanerThreads);
		}
		this.numCleanerThreads = numCleanerThreads;
	}

	@Override
	public long getMaximumVirtualMapSize() {
		return maximumVirtualMapSize;
	}

	public void setMaximumVirtualMapSize(final long maximumVirtualMapSize) {
		if (maximumVirtualMapSize < 1 || maximumVirtualMapSize > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Cannot configure maximumVirtualMapSize=" + maximumVirtualMapSize);
		}
		this.maximumVirtualMapSize = maximumVirtualMapSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getVirtualMapWarningThreshold() {
		return virtualMapWarningThreshold;
	}

	public void setVirtualMapWarningThreshold(final long virtualMapWarningThreshold) {
		if (virtualMapWarningThreshold < 0 || virtualMapWarningThreshold > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Cannot configure virtualMapWarningThreshold=" + virtualMapWarningThreshold);
		}
		this.virtualMapWarningThreshold = virtualMapWarningThreshold;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getVirtualMapWarningInterval() {
		return virtualMapWarningInterval;
	}

	public void setVirtualMapWarningInterval(final long virtualMapWarningInterval) {
		if (virtualMapWarningInterval < 1 || virtualMapWarningInterval > virtualMapWarningThreshold) {
			throw new IllegalArgumentException("Cannot configure virtualMapWarningInterval=" + virtualMapWarningInterval);
		}
		this.virtualMapWarningInterval = virtualMapWarningInterval;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getFlushInterval() {
		return flushInterval;
	}

	public void setFlushInterval(final int flushInterval) {
		if (flushInterval < 1) {
			throw new IllegalArgumentException("Cannot configure flushInterval=" + flushInterval);
		}
		this.flushInterval = flushInterval;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getPreferredFlushQueueSize() {
		return preferredFlushQueueSize;
	}

	/**
	 * Set the preferred maximum number of virtual maps (in a fast copy family) that are waiting to be flushed.
	 */
	public void setPreferredFlushQueueSize(final int preferredFlushQueueSize) {
		this.preferredFlushQueueSize = preferredFlushQueueSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getFlushThrottleStepSize() {
		return flushThrottleStepSize;
	}

	/**
	 * For every map copy that is awaiting flushing in excess of {@link #getPreferredFlushQueueSize()}, artificially
	 * increase the amount of time required to make a fast copy by this amount of time.
	 */
	public void setFlushThrottleStepSize(final Duration flushThrottleStepSize) {
		this.flushThrottleStepSize = flushThrottleStepSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getMaximumFlushThrottlePeriod() {
		return maximumFlushThrottlePeriod;
	}

	/**
	 * Set the maximum amount of time that any virtual map fast copy will be delayed due to a flush backlog.
	 */
	public void setMaximumFlushThrottlePeriod(final Duration maximumFlushThrottlePeriod) {
		this.maximumFlushThrottlePeriod = maximumFlushThrottlePeriod;
	}
}

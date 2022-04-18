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

package com.swirlds.virtualmap;

import java.time.Duration;

/**
 * {@link VirtualMapSettings} implementation with all defaults. Necessary for testing
 * {@link VirtualMapSettingsFactory} client code running in an environment without Browser-configured settings.
 */
public final class DefaultVirtualMapSettings implements VirtualMapSettings {
	public static final int DEFAULT_NUM_HASH_THREADS = -1;
	public static final double DEFAULT_PERCENT_HASH_THREADS = 50.0;
	public static final int DEFAULT_NUM_CLEANER_THREADS = -1;
	public static final double DEFAULT_PERCENT_CLEANER_THREADS = 25.0;
	public static final long DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE = Integer.MAX_VALUE;
	public static final long DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD = 5_000_000;
	public static final long DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL = 100_000;
	public static final int DEFAULT_FLUSH_INTERVAL = 20;
	public static final int DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE = 2;
	public static final Duration DEFAULT_FLUSH_THROTTLE_STEP_SIZE = Duration.ofMillis(200);
	public static final Duration DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD = Duration.ofSeconds(5);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getPercentHashThreads() {
		return DEFAULT_PERCENT_HASH_THREADS;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumHashThreads() {
		final int threads = Integer.getInteger("hashingThreadCount",
				(int) (Runtime.getRuntime().availableProcessors() * (getPercentHashThreads() / UNIT_FRACTION_PERCENT)));

		return Math.max(1, threads);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getPercentCleanerThreads() {
		return DEFAULT_PERCENT_CLEANER_THREADS;
	}

	@Override
	public int getNumCleanerThreads() {
		final int numProcessors = Runtime.getRuntime().availableProcessors();
		final int threads = Integer.getInteger("cleanerThreadCount",
				(int) (numProcessors * (getPercentCleanerThreads() / UNIT_FRACTION_PERCENT)));

		return Math.max(1, threads);
	}

	@Override
	public long getMaximumVirtualMapSize() {
		return DEFAULT_MAXIMUM_VIRTUAL_MAP_SIZE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getVirtualMapWarningThreshold() {
		return DEFAULT_VIRTUAL_MAP_WARNING_THRESHOLD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getVirtualMapWarningInterval() {
		return DEFAULT_VIRTUAL_MAP_WARNING_INTERVAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getFlushInterval() {
		return DEFAULT_FLUSH_INTERVAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getPreferredFlushQueueSize() {
		return DEFAULT_PREFERRED_FLUSH_QUEUE_SIZE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getFlushThrottleStepSize() {
		return DEFAULT_FLUSH_THROTTLE_STEP_SIZE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Duration getMaximumFlushThrottlePeriod() {
		return DEFAULT_MAXIMUM_FLUSH_THROTTLE_PERIOD;
	}

}

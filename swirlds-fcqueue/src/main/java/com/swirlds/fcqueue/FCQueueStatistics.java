/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.fcqueue;

import com.swirlds.common.system.Platform;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.system.SwirldMain;

/**
 * Singleton factory for loading and registering {@link FCQueue} statistics. This is the primary entry point for all
 * {@link SwirldMain} implementations that wish to track {@link FCQueue} statistics.
 */
public class FCQueueStatistics {

	/**
	 * true if these statistics have been registered by the application; otherwise false
	 */
	private static volatile boolean registered;

	/**
	 * default half-life for statistics
	 */
	private static final double DEFAULT_HALF_LIFE = 10;

	/**
	 * avg time taken to execute the FCQueue add method, including locks (in microseconds)
	 */
	protected static final StatsRunningAverage fcqAddExecutionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCQueue remove method, including locks (in microseconds)
	 */
	protected static final StatsRunningAverage fcqRemoveExecutionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCQueue getHash method, including locks (in microseconds)
	 */
	protected static final StatsRunningAverage fcqHashExecutionMicros = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	private static final String FCQUEUE_CATEGORY = "FCQueue";

	/**
	 * Default private constructor to ensure that this may not be instantiated.
	 */
	private FCQueueStatistics() {

	}

	/**
	 * Registers the {@link FCQueue} statistics with the specified {@link Platform} instance. Delegates to the {@link
	 * #register(Platform, boolean)} method and defaults to including lock timings.
	 *
	 * @param platform
	 * 		the platform instance
	 */
	public static void register(final Platform platform) {
		register(platform, true);
	}

	/**
	 * Gets a value indicating whether the {@link SwirldMain} has called the {@link
	 * #register(Platform)} method on this factory.
	 *
	 * @return true if these statistics have been registered by the application; otherwise false
	 */
	public static boolean isRegistered() {
		return registered;
	}

	/**
	 * Registers the {@link FCQueue} statistics with the specified {@link Platform} instance.
	 *
	 * @param platform
	 * 		the platform instance
	 * @param includeLocks
	 * 		true if lock acquisition timings should be reported; otherwise false if no lock timings should be reported
	 */
	public static void register(final Platform platform, final boolean includeLocks) {
		platform.addAppStatEntry(new StatEntry(
				FCQUEUE_CATEGORY,
				"fcqAddExecMicroSec",
				"avg time taken to execute the FCQueue add method, not including locks (in microseconds)",
				"%,9.6f",
				fcqAddExecutionMicros,
				(h) -> {
					fcqAddExecutionMicros.reset(h);
					return fcqAddExecutionMicros;
				},
				null,
				fcqAddExecutionMicros::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				FCQUEUE_CATEGORY,
				"fcqRemoveExecMicroSec",
				"avg time taken to execute the FCQueue remove method, not including locks (in microseconds)",
				"%,9.6f",
				fcqRemoveExecutionMicros,
				(h) -> {
					fcqRemoveExecutionMicros.reset(h);
					return fcqRemoveExecutionMicros;
				},
				null,
				fcqRemoveExecutionMicros::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				FCQUEUE_CATEGORY,
				"fcqHashExecMicroSec",
				"avg time taken to execute the FCQueue remove method, not including locks (in microseconds)",
				"%,9.6f",
				fcqHashExecutionMicros,
				(h) -> {
					fcqHashExecutionMicros.reset(h);
					return fcqHashExecutionMicros;
				},
				null,
				fcqHashExecutionMicros::getWeightedMean
		));

		registered = true;
	}
}

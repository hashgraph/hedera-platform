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

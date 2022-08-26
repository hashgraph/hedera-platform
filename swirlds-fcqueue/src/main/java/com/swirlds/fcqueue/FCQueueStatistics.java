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

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;

/**
 * Singleton factory for loading and registering {@link FCQueue} statistics. This is the primary entry point for all
 * {@link SwirldMain} implementations that wish to track {@link FCQueue} statistics.
 */
public class FCQueueStatistics {

	private static final String FCQUEUE_CATEGORY = "FCQueue";

	/**
	 * true if these statistics have been registered by the application; otherwise false
	 */
	private static volatile boolean registered;

	/**
	 * avg time taken to execute the FCQueue add method, including locks (in microseconds)
	 */
	protected static final RunningAverageMetric fcqAddExecutionMicros = new RunningAverageMetric(
			FCQUEUE_CATEGORY,
			"fcqAddExecMicroSec",
			"avg time taken to execute the FCQueue add method, not including locks (in microseconds)",
			FORMAT_9_6
	);

	/**
	 * avg time taken to execute the FCQueue remove method, including locks (in microseconds)
	 */
	protected static final RunningAverageMetric fcqRemoveExecutionMicros = new RunningAverageMetric(
			FCQUEUE_CATEGORY,
			"fcqRemoveExecMicroSec",
			"avg time taken to execute the FCQueue remove method, not including locks (in microseconds)",
			FORMAT_9_6
	);

	/**
	 * avg time taken to execute the FCQueue getHash method, including locks (in microseconds)
	 */
	protected static final RunningAverageMetric fcqHashExecutionMicros = new RunningAverageMetric(
			FCQUEUE_CATEGORY,
			"fcqHashExecMicroSec",
			"avg time taken to execute the FCQueue remove method, not including locks (in microseconds)",
			FORMAT_9_6
	);

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
		platform.addAppMetrics(
				fcqAddExecutionMicros,
				fcqRemoveExecutionMicros,
				fcqHashExecutionMicros
		);

		registered = true;
	}
}

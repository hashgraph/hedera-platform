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

package com.swirlds.merkle.map;

import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_3;

/**
 * Singleton factory for loading and registering {@link MerkleMap} statistics. This is the primary entry point for all
 * {@link SwirldMain} implementations that wish to track {@link MerkleMap} statistics.
 */
public final class MerkleMapStatistics {

	private static final String MM_CATEGORY = "MM";

	/**
	 * true if these statistics have been registered by the application; otherwise false
	 */
	private static volatile boolean registered;

	/**
	 * avg time taken to execute the MerkleMap get method (in microseconds)
	 */
	static final RunningAverageMetric mmmGetMicroSec = new RunningAverageMetric(
			MM_CATEGORY,
			"mmGetMicroSec",
			"avg time taken to execute the MerkleMap get method (in microseconds)",
			FORMAT_11_3
	);

	/**
	 * avg time taken to execute the MerkleMap getForModify method (in microseconds)
	 */
	static final RunningAverageMetric mmGfmMicroSec = new RunningAverageMetric(
			MM_CATEGORY,
			"mmGfmMicroSec",
			"avg time taken to execute the MerkleMap getForModify method (in microseconds)",
			FORMAT_11_3
	);

	/**
	 * avg time taken to execute the MerkleMap replace method (in microseconds)
	 */
	static final RunningAverageMetric mmReplaceMicroSec = new RunningAverageMetric(
			MM_CATEGORY,
			"mmReplaceMicroSec",
			"avg time taken to execute the MerkleMap replace method (in microseconds)",
			FORMAT_11_3
	);

	/**
	 * avg time taken to execute the MerkleMap put method (in microseconds)
	 */
	static final RunningAverageMetric mmPutMicroSec = new RunningAverageMetric(
			MM_CATEGORY,
			"mmPutMicroSec",
			"avg time taken to execute the MerkleMap put method (in microseconds)",
			FORMAT_11_3
	);

	/**
	 * Default private constructor to ensure that this may not be instantiated.
	 */
	private MerkleMapStatistics() {
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
	 * Registers the {@link MerkleMap} statistics with the specified {@link Platform} instance.
	 *
	 * @param platform
	 * 		the platform instance
	 */
	public static void register(final Platform platform) {
		platform.addAppMetrics(
				mmmGetMicroSec,
				mmGfmMicroSec,
				mmReplaceMicroSec,
				mmPutMicroSec
		);

		registered = true;
	}
}

/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.fcmap;

import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;

/**
 * Singleton factory for loading and registering {@link FCMap} statistics. This is the primary entry point for all
 * {@link com.swirlds.common.SwirldMain} implementations that wish to track {@link FCMap} statistics.
 */
public class FCMapStatistics {

	/**
	 * true if these statistics have been registered by the application; otherwise false
	 */
	private static volatile boolean registered;

	/**
	 * default half-life for statistics
	 */
	private static final double DEFAULT_HALF_LIFE = 10;

	/**
	 * avg time taken to execute the FCMap get method (in microseconds)
	 */
	protected static final StatsRunningAverage fcmGetMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCMap getForModify method (in microseconds)
	 */
	protected static final StatsRunningAverage fcmGfmMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCMap replace method (in microseconds)
	 */
	protected static final StatsRunningAverage fcmReplaceMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the FCMap put method (in microseconds)
	 */
	protected static final StatsRunningAverage fcmPutMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	private static final String FCM_CATEGORY = "FCM";
	private static final String FORMAT_FLOAT_3SIGFIG = "%,11.3f";

	/**
	 * Default private constructor to ensure that this may not be instantiated.
	 */
	private FCMapStatistics() {
	}

	/**
	 * Gets a value indicating whether the {@link com.swirlds.common.SwirldMain} has called the {@link
	 * #register(Platform)} method on this factory.
	 *
	 * @return true if these statistics have been registered by the application; otherwise false
	 */
	public static boolean isRegistered() {
		return registered;
	}

	/**
	 * Registers the {@link FCMap} statistics with the specified {@link Platform} instance.
	 *
	 * @param platform
	 * 		the platform instance
	 */
	public static void register(final Platform platform) {
		platform.addAppStatEntry(new StatEntry(
				FCM_CATEGORY,
				"fcmGetMicroSec",
				"avg time taken to execute the FCMap get method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				fcmGetMicroSec,
				h -> {
					fcmGetMicroSec.reset(h);
					return fcmGetMicroSec;
				},
				null,
				fcmGetMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				FCM_CATEGORY,
				"fcmGfmMicroSec",
				"avg time taken to execute the FCMap getForModify method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				fcmGfmMicroSec,
				h -> {
					fcmGfmMicroSec.reset(h);
					return fcmGfmMicroSec;
				},
				null,
				fcmGfmMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				FCM_CATEGORY,
				"fcmReplaceMicroSec",
				"avg time taken to execute the FCMap replace method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				fcmReplaceMicroSec,
				h -> {
					fcmReplaceMicroSec.reset(h);
					return fcmReplaceMicroSec;
				},
				null,
				fcmReplaceMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				FCM_CATEGORY,
				"fcmPutMicroSec",
				"avg time taken to execute the FCMap put method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				fcmPutMicroSec,
				h -> {
					fcmPutMicroSec.reset(h);
					return fcmPutMicroSec;
				},
				null,
				fcmPutMicroSec::getWeightedMean
		));

		registered = true;
	}
}

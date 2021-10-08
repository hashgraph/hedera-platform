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

package com.swirlds.merkle.map;

import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;

/**
 * Singleton factory for loading and registering {@link MerkleMap} statistics. This is the primary entry point for all
 * {@link com.swirlds.common.SwirldMain} implementations that wish to track {@link MerkleMap} statistics.
 */
public final class MerkleMapStatistics {

	/**
	 * true if these statistics have been registered by the application; otherwise false
	 */
	private static volatile boolean registered;

	/**
	 * default half-life for statistics
	 */
	private static final double DEFAULT_HALF_LIFE = 10;

	/**
	 * avg time taken to execute the MerkleMap get method (in microseconds)
	 */
	protected static final StatsRunningAverage mmmGetMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the MerkleMap getForModify method (in microseconds)
	 */
	protected static final StatsRunningAverage mmGfmMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the MerkleMap replace method (in microseconds)
	 */
	protected static final StatsRunningAverage mmReplaceMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	/**
	 * avg time taken to execute the MerkleMap put method (in microseconds)
	 */
	protected static final StatsRunningAverage mmPutMicroSec = new StatsRunningAverage(DEFAULT_HALF_LIFE);

	private static final String MM_CATEGORY = "MM";
	private static final String FORMAT_FLOAT_3SIGFIG = "%,11.3f";

	/**
	 * Default private constructor to ensure that this may not be instantiated.
	 */
	private MerkleMapStatistics() {
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
	 * Registers the {@link MerkleMap} statistics with the specified {@link Platform} instance.
	 *
	 * @param platform
	 * 		the platform instance
	 */
	public static void register(final Platform platform) {
		platform.addAppStatEntry(new StatEntry(
				MM_CATEGORY,
				"mmGetMicroSec",
				"avg time taken to execute the MerkleMap get method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				mmmGetMicroSec,
				h -> {
					mmmGetMicroSec.reset(h);
					return mmmGetMicroSec;
				},
				null,
				mmmGetMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				MM_CATEGORY,
				"mmGfmMicroSec",
				"avg time taken to execute the MerkleMap getForModify method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				mmGfmMicroSec,
				h -> {
					mmGfmMicroSec.reset(h);
					return mmGfmMicroSec;
				},
				null,
				mmGfmMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				MM_CATEGORY,
				"mmReplaceMicroSec",
				"avg time taken to execute the MerkleMap replace method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				mmReplaceMicroSec,
				h -> {
					mmReplaceMicroSec.reset(h);
					return mmReplaceMicroSec;
				},
				null,
				mmReplaceMicroSec::getWeightedMean
		));

		platform.addAppStatEntry(new StatEntry(
				MM_CATEGORY,
				"mmPutMicroSec",
				"avg time taken to execute the MerkleMap put method (in microseconds)",
				FORMAT_FLOAT_3SIGFIG,
				mmPutMicroSec,
				h -> {
					mmPutMicroSec.reset(h);
					return mmPutMicroSec;
				},
				null,
				mmPutMicroSec::getWeightedMean
		));

		registered = true;
	}
}

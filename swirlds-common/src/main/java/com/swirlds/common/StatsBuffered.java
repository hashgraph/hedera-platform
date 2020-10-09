/*
 * (c) 2016-2020 Swirlds, Inc.
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
package com.swirlds.common;

import com.swirlds.common.internal.StatsBuffer;

/**
 * A statistic such as StatsSpeedometer or StatsRunningAverage should implement this if it will
 * contain a
 * {@link StatsBuffer} for recent history and another for all of history. The user can then retrieve them from
 * {@link Statistics}.
 */
public interface StatsBuffered {
	/**
	 * get the entire history of values of this statistic. The caller should not modify it.
	 *
	 * @return A {@link StatsBuffer} object keeps all history statistic.
	 */
	StatsBuffer getAllHistory();

	/**
	 * get the recent history of values of this statistic. The caller should not modify it.
	 *
	 * @return A {@link StatsBuffer} object keeps recent history of this statistic.
	 */
	StatsBuffer getRecentHistory();

	/**
	 * reset the statistic, and make it use the given halflife
	 *
	 * @param halflife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 */
	void reset(double halflife);

	/**
	 * get average of values per cycle()
	 *
	 * @return average of values
	 */
	double getMean();

	/**
	 * get maximum value from all the values of this statistic
	 *
	 * @return maximum value
	 */
	double getMax();

	/**
	 * get minimum value from all the values of this statistic
	 *
	 * @return minimum value
	 */
	double getMin();

	/**
	 * get standard deviation of this statistic
	 *
	 * @return standard deviation
	 */
	double getStdDev();
}

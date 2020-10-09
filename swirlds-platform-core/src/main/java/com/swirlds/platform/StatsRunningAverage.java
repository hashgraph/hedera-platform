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
package com.swirlds.platform;

import com.swirlds.common.StatsBuffered;
import com.swirlds.common.internal.StatsBuffer;

/**
 * This class maintains a running average of some numeric value. It is exponentially weighted in time, with
 * a given half life. If it is always given the same value, then that value will be the average, regardless
 * of the timing.
 */
public class StatsRunningAverage implements StatsBuffered {
	/** the estimated running average */
	private double mean = 0;
	/** each recordValue(X) counts as X calls to values.cycle() */
	private StatsSpeedometer values;
	/** each recordValue(X) counts as 1 call to times.cycle() */
	private StatsSpeedometer times;
	/** Did we just perform a reset, and are about to record the first value? */
	boolean firstRecord = true;

	// FORMULA: mean = values.cyclesPerSeconds() / times.cyclesPerSecond()

	/** the entire history of means of this RunningAverage */
	private StatsBuffer allHistory;
	/** the recent history of means of this RunningAverage */
	private StatsBuffer recentHistory;

	/**
	 * get the entire history of values of means of this RunningAverage. The caller should not modify it.
	 */
	@Override
	public StatsBuffer getAllHistory() {
		return allHistory;
	}

	/**
	 * get the recent history of values of means of this RunningAverage. The caller should not modify it.
	 */
	@Override
	public StatsBuffer getRecentHistory() {
		return recentHistory;
	}

	/**
	 * instantiation a RunningAverage and start the measurements right now.
	 */
	StatsRunningAverage() {
		this(10); // default: half the weight is in the last 10 seconds, for weighted average
	}

	/**
	 * Instantiate a RunningAverage with the given halfLife and start the measurements right now. This will
	 * calculate exponentially weighted averages of the values passed to recordValue(), where the
	 * exponential weighting has a half life of halfLife seconds.
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 */
	public StatsRunningAverage(double halfLife) {
		reset(halfLife);
	}

	/**
	 * Start over on the measurements and counts, to get an exponentially-weighted average of the values
	 * passed to recordValue(), with the weighting having a half life of halfLife seconds. This is
	 * equivalent to instantiating a new RunningAverage.
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 */
	@Override
	public void reset(double halfLife) {
		firstRecord = true;
		values = new StatsSpeedometer(halfLife, false);
		times = new StatsSpeedometer(halfLife, false);
		allHistory = new StatsBuffer(Settings.statsBufferSize, 0,
				Settings.statsSkipSeconds);
		recentHistory = new StatsBuffer(Settings.statsBufferSize,
				Settings.statsRecentSeconds, 0);
	}

	/**
	 * Incorporate "value" into the running average. If it is the same on every call, then the average will
	 * equal it, no matter how those calls are timed. If it has various values on various calls, then the
	 * running average will weight the more recent ones more heavily, with a half life of halfLife seconds,
	 * where halfLife was passed in when this object was instantiated.
	 * <p>
	 * If this is called repeatedly with a value of X over a long period, then suddenly all calls start
	 * having a value of Y, then after halflife seconds, the average will have moved halfway from X to Y,
	 * regardless of how often update was called, as long as it is called at least once at the end of that
	 * period.
	 *
	 * @param value
	 * 		the value to incorporate into the running average
	 */
	public void recordValue(double value) {
		if (Double.isNaN(value)) { //java getSystemCpuLoad returns NaN at beginning
			return;
		}
		if (firstRecord || value == mean) {
			// if the same value is always given since the beginning, then avoid roundoff errors
			firstRecord = false;
			values.update(value);
			times.update(1);
			mean = value;
		} else {
			mean = values.update(value) / times.update(1);
		}
		allHistory.record(mean);
		recentHistory.record(mean);
	}

	/**
	 * Get the average of recent calls to recordValue(). This is an exponentially-weighted average of recent
	 * calls, with the weighting by time, not by number of calls to recordValue().
	 *
	 * @return the running average as of the last time recordValue was called
	 */
	public double getWeightedMean() {
		return mean;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getMean() {
		return mean;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getMax() { // if allHistory is empty return recentHistory value
		if (allHistory.numBins() > 0) {
			return allHistory.yMaxMostRecent();
		} else if (recentHistory.numBins() > 0) {
			return recentHistory.yMaxMostRecent();
		} else {
			return 0; // return 0 when bins are empty to avoid put MAX_VALUE in output
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getMin() { // if allHistory is empty return recentHistory value
		if (allHistory.numBins() > 0) {
			return allHistory.yMinMostRecent();
		} else if (recentHistory.numBins() > 0) {
			return recentHistory.yMinMostRecent();
		} else {
			return 0; // return 0 when bins are empty to avoid put MAX_VALUE in output
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getStdDev() { // if allHistory is empty return recentHistory value
		if (allHistory.numBins() > 0) {
			return allHistory.yStdMostRecent();
		} else if (recentHistory.numBins() > 0) {
			return recentHistory.yStdMostRecent();
		} else {
			return 0; // return 0 when bins are empty to avoid put MAX_VALUE in output
		}
	}

}

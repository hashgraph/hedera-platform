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
 * This class measures how many times per second the cycle() method is called. It is recalculated every
 * period, where its period is 0.1 seconds by default. If instantiated with gamma=0.9, then half the
 * weighting comes from the last 7 periods. If 0.99 it's 70 periods, 0.999 is 700, etc.
 * <p>
 * The timer starts at instantiation, and can be reset with the reset() method.
 */
public class StatsSpeedometer implements StatsBuffered {
	private static final double ln2 = Math.log(2);
	/** find average since this time */
	private long startTime = System.nanoTime();
	/** the last time update() was called */
	private long lastTime = System.nanoTime();
	/** estimated average calls/sec to cycle() */
	private double cyclesPerSecond = 0;
	/** half the weight = this many sec */
	private double halfLife = 7;
	/** the entire history of values of this speedometer */
	private StatsBuffer allHistory = null;
	/** the recent history of values of this speedometer */
	private StatsBuffer recentHistory = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StatsBuffer getAllHistory() {
		return allHistory;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StatsBuffer getRecentHistory() {
		return recentHistory;
	}

	/**
	 * instantiation a speedometer and start the measurements right now.
	 */
	StatsSpeedometer() {
		this(10, false); // default: half the weight is in the last 10 seconds, for weighted average
	}

	/**
	 * Instantiate a Speedometer with the given halfLife and start the measurements right now. This will
	 * calculate exponentially weighted averages of the number of times update() is called per second. Where
	 * the exponential weighting has a half life of halfLife seconds. This will record the history, so it is
	 * the same as using the constructor new StatsSpeedometer(halfLife, true).
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 */
	public StatsSpeedometer(double halfLife) {
		reset(halfLife);
	}

	/**
	 * Instantiate a Speedometer with the given halfLife and start the measurements right now. This will
	 * calculate exponentially weighted averages of the number of times update() is called per second. Where
	 * the exponential weighting has a half life of halfLife seconds.
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 * @param saveHistory
	 * 		true if a StatsBuffer of recent and all history should be created and used
	 */
	StatsSpeedometer(double halfLife, boolean saveHistory) {
		reset(halfLife, saveHistory);
	}

	/**
	 * Start over on the measurements and counts, to get an exponentially-weighted average number of calls
	 * to cycle() per second, with the weighting having a half life of halfLife seconds. This is equivalent
	 * to instantiating a new Speedometer. This will also record a history of values, so calling this is the
	 * same as calling reset(halfLife, true).
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 */
	@Override
	public void reset(double halfLife) {
		reset(halfLife, true);
	}

	/**
	 * Start over on the measurements and counts, to get an exponentially-weighted average number of calls
	 * to cycle() per second, with the weighting having a half life of halfLife seconds. This is equivalent
	 * to instantiating a new Speedometer. If halfLife < 0.01 then 0.01 will be used.
	 *
	 * @param halfLife
	 * 		half of the exponential weighting comes from the last halfLife seconds
	 * @param saveHistory
	 * 		true if a StatsBuffer of recent and all history should be created and used
	 */
	void reset(double halfLife, boolean saveHistory) {
		this.halfLife = Math.max(0.01, halfLife); // clip to 0.01 to avoid division by zero problems
		startTime = System.nanoTime(); // find average since this time
		lastTime = startTime; // the last time update() was called
		cyclesPerSecond = 0; // estimated average calls to cycle() per second
		if (saveHistory) {
			allHistory = new StatsBuffer(Settings.statsBufferSize, 0,
					Settings.statsSkipSeconds);
			recentHistory = new StatsBuffer(Settings.statsBufferSize,
					Settings.statsRecentSeconds, 0);
		} else {
			allHistory = null;
			recentHistory = null;
		}
	}

	/**
	 * Get the average number of times per second the cycle() method was called. This is an
	 * exponentially-weighted average of recent timings.
	 *
	 * @return the estimated number of calls to cycle() per second, recently
	 */
	public double getCyclesPerSecond() {
		// return a value discounted to right now, but don't save it as a data point
		return update(0, false);
	}

	/**
	 * This is the method to call repeatedly. The average calls per second will be calculated.
	 *
	 * @return the estimated number of calls to cycle() per second, recently
	 */
	double cycle() {
		return update(1);
	}

	/**
	 * calling update(N) is equivalent to calling cycle() N times almost simultaneously. Calling cycle() is
	 * equivalent to calling update(1). Calling update(0) will update the estimate of the cycles per second
	 * by looking at the current time (to update the seconds) without incrementing the count of the cycles.
	 * So calling update(0) repeatedly with no calls to cycle() will cause the cycles per second estimate to
	 * go asymptotic to zero.
	 * <p>
	 * The speedometer initially keeps a simple, uniformly-weighted average of the number of calls to
	 * cycle() per second since the start of the run. Over time, that makes each new call to cycle() have
	 * less weight (because there are more of them). Eventually, the weight of a new call drops below the
	 * weight it would have under the exponentially-weighted average. At that point, it switches to the
	 * exponentially-weighted average.
	 *
	 * @param numCycles
	 * 		number of cycles to record
	 * @return estimated number of calls to cycle() per second
	 */
	public synchronized double update(double numCycles) {
		return update(numCycles, true);
	}

	/**
	 * The same as update(numCycles), except this will only record a new data point if recordData==true
	 *
	 * @param numCycles
	 * 		number of cycles to record
	 * @return estimated number of calls to cycle() per second
	 */
	private synchronized double update(double numCycles, boolean recordData) {
		long currentTime = System.nanoTime();
		double t1 = ((double) (lastTime - startTime)) / 1.0e9; // seconds: start to last update
		double t2 = ((double) (currentTime - startTime)) / 1.0e9; // seconds: start to now
		double dt = ((double) (currentTime - lastTime)) / 1.0e9; // seconds: last update to now
		if (1. / t2 > ln2 / halfLife) { // during startup period, so do uniformly-weighted average
			cyclesPerSecond = (cyclesPerSecond * t1 + numCycles) / t2;
		} else { // after startup, so do exponentially-weighted average with given half life
			cyclesPerSecond = cyclesPerSecond * Math.pow(0.5, dt / halfLife)
					+ numCycles * ln2 / halfLife;
		}
		lastTime = currentTime;
		if (allHistory != null && recordData) {
			allHistory.record(cyclesPerSecond);
			recentHistory.record(cyclesPerSecond);
		}
		return cyclesPerSecond;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getMean() {
		return cyclesPerSecond;
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

// derivation of the formulas in update():
// if only update(n) is called (not cycle), and if it is always called
// exactly once a second since the start, then exponential weighting would be:
// cyclesPerSecond = gamma * cyclesPerSecond + (1-gamma) * n
// It it easy to verify that if we want a half life of halfLife seconds, we use:
// gamma = Math.pow(0.5, 1 / halfLife)
// If we have skipped calling update() for the last dt seconds,
// each of which should have been calls to update(0), but
// those calls weren't made, then calling update(n) now should do:
// cyclesPerSecond = Math.pow(gamma, dt) * cyclesPerSecond + (1-gamma) * n
// Suppose the calls to update(n) aren't once a second, but are K times a second.
// Then we should multiply all 3 variables (dt, halfLife, n) by K. Plugging
// gamma into the first equation, and taking the limit as K goes to infinity gives:
// cyclesPerSecond = Math.pow(0.5, dt / halfLife) * cyclesPerSecond + ln2 / halfLife * n
// which is the formula used in the function above.
// The other formula (during startup) is simpler. If cyclesPerSecond is the average
// cycle calls per second during the first t1 seconds, then multiplying it by t1 gives the count
// of cycle calls up to then. Adding numCycles gives the count up to now. Dividing by t2 gives
// the average per second up to now. So the first formula is the un-weighted average. The
// if statement switches between them as soon as the weight on numCycles for un-weighted
// drops below the weight for weighted.

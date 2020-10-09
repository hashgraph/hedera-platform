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
package com.swirlds.common.internal;

import java.time.Instant;

/**
 * Keep a running history of a double value vs. time. History is divided into at most maxbins different
 * bins. It records the min and max value, and the min and max time, for each bin.
 *
 * There are many class variables. Other classes should only read, not write, those variables and the
 * elements of their arrays. This is not thread safe. There must never be one thread reading these variables
 * while another thread is calling any of the methods.
 */
public class StatsBuffer {
	/** 0 if storing all of history. Else store only this many seconds of recent history */
	final double recentSeconds;

	/** the time record() was first called, in seconds since the start of the epoch */
	private double start = -1;

	/**
	 * record() will ignore all inputs for startDelay seconds, starting from the first time it's called
	 */
	double startDelay = 0;

	/**
	 * 0 if storing all of history. Else store only recent history where each bin covers binSeconds seconds.
	 */
	private final double binSeconds;
	/** store at most this many bins in the history */
	private final int maxBins;

	/** min of the all x values in each bin */
	private final double[] xMins;
	/** max of the all x values in each bin */
	private final double[] xMaxs;
	/** min of the all y values in each bin */
	private final double[] yMins;
	/** max of the all y values in each bin */
	private final double[] yMaxs;
	/** average of all the y values in each bin */
	private final double[] yAvgs;
	/** variance of all the y values in each bin (squared standard deviation, NOT Bessel corrected) */
	private final double[] yVars;

	/** number of bins currently stored in all the arrays */
	private int numBins = 0;
	/** index in all arrays of the bin with the oldest data */
	private int firstBin = 0;
	/** index in all arrays of the bin currently being added to (-1 if no bins exist) */
	private int currBin = -1;

	/** if binSeconds==0, then this is the number of samples in each bin other than the last */
	private long numPerBin = 1;
	/** if binSeconds==0, then this is number of samples in the last bin */
	private long numLastBin = 0;

	/**
	 * Store a history of samples combined into at most maxBins bins. If recentSeconds is zero, then all of
	 * history is stored, with an equal number of samples in each bin. Otherwise, only the last
	 * recentSeconds seconds of history is stored, with maxBins different bins each covering an equal
	 * fraction of that period.
	 *
	 * If recentSeconds &gt; 0, then empty bins are not stored, so some of the older bins (more than
	 * recentSeconds old) can continue to exist until enough newer bins are collected to discard them.
	 *
	 * The maxBins must be even. If it's odd, it will be incremented, so passing in 99 is the same as
	 * passing in 100.
	 *
	 * @param maxBins
	 * 		the maximum number of bins to store (must be even)
	 * @param recentSeconds
	 * 		the max period of time covered by all the stored data, in seconds (or 0 if covering all of
	 * 		history)
	 * @param startDelay
	 * 		record() will ignore all inputs for the first startDelay seconds, starting from the first
	 * 		time it's called
	 */
	public StatsBuffer(int maxBins, double recentSeconds, double startDelay) {
		this.maxBins = maxBins + (maxBins % 2); // add 1 if necessary to make it even
		this.binSeconds = recentSeconds / maxBins;
		this.recentSeconds = recentSeconds;
		this.startDelay = startDelay;
		xMins = new double[this.maxBins];
		xMaxs = new double[this.maxBins];
		yMins = new double[this.maxBins];
		yMaxs = new double[this.maxBins];
		yAvgs = new double[this.maxBins];
		yVars = new double[this.maxBins];
	}

	/** get the number of bins currently in use */
	public int numBins() {
		return numBins;
	}

	/**
	 * return the average of all x values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double xAvg(int i) {
		int ii = (firstBin + i) % numBins;
		return (xMins[ii] + xMaxs[ii]) / 2;
	}

	/**
	 * return the average of all y values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double yAvg(int i) {
		int ii = (firstBin + i) % numBins;
		return yAvgs[ii];
	}

	/**
	 * return the min of all x values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double xMin(int i) {
		int ii = (firstBin + i) % numBins;
		return xMins[ii];
	}

	/**
	 * return the min of all y values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double yMin(int i) {
		int ii = (firstBin + i) % numBins;
		return yMins[ii];
	}

	/**
	 * return the max of all x values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double xMax(int i) {
		int ii = (firstBin + i) % numBins;
		return xMaxs[ii];
	}

	/**
	 * return the max of all y values in bin i, where i=0 is the oldest and i=numBins-1 is the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double yMax(int i) {
		int ii = (firstBin + i) % numBins;
		return yMaxs[ii];
	}

	/**
	 * return the standard deviation of all y values in bin i, where i=0 is the oldest and i=numBins-1 is
	 * the newest.
	 *
	 * @param i
	 * 		the index for the bin
	 * @return the average
	 */
	public double yStd(int i) {
		int ii = (firstBin + i) % numBins;
		return Math.sqrt(yVars[ii]);
	}


	/**
	 * return the standard deviation of most recent values
	 *
	 * @return the standard deviation
	 */
	public double yStdMostRecent() {
		if (numBins > 0) {
			int ii = (currBin) % numBins;
			return Math.sqrt(yVars[ii]);
		} else {
			return 0;
		}
	}

	/**
	 * find the minimum of all stored y values of from the most recent bin
	 *
	 * @return the minimum y value
	 */
	public double yMinMostRecent() {
		if (numBins > 0) {
			int ii = (currBin) % numBins;
			return yMins[ii];
		} else {
			return 0;
		}
	}

	/**
	 * find the maximum of all stored y values from the most recent bin
	 *
	 * @return the maximum y value
	 */
	public double yMaxMostRecent() {
		if (numBins > 0) {
			int ii = (currBin) % numBins;
			return yMaxs[ii];
		} else {
			return 0;
		}
	}

	/**
	 * find the minimum of all stored x values
	 *
	 * @return the minimum x value
	 */
	public double xMin() {
		double v = Double.MAX_VALUE;
		for (int i = 0; i < numBins; i++) {
			v = Math.min(v, xMins[(firstBin + i) % maxBins]);
		}
		return v;
	}

	/**
	 * find the maximum of all stored x values
	 *
	 * @return the maximum x value
	 */
	public double xMax() {
		double v = -Double.MAX_VALUE;
		for (int i = 0; i < numBins; i++) {
			v = Math.max(v, xMaxs[(firstBin + i) % maxBins]);
		}
		return v;
	}

	/**
	 * find the minimum of all stored y values
	 *
	 * @return the minimum y value
	 */
	public double yMin() {
		double v = Double.MAX_VALUE;
		for (int i = 0; i < numBins; i++) {
			v = Math.min(v, yMins[(firstBin + i) % maxBins]);
		}
		return v;
	}

	/**
	 * find the maximum of all stored y values
	 *
	 * @return the maximum y value
	 */
	public double yMax() {
		double v = -Double.MAX_VALUE;
		for (int i = 0; i < numBins; i++) {
			v = Math.max(v, yMaxs[(firstBin + i) % maxBins]);
		}
		return v;
	}

	/**
	 * the age (number of seconds since the start of the epoch) right now
	 */
	public double xNow() {
		Instant now = Instant.now();
		return now.getEpochSecond() + (((double) now.getNano()) / 1e9);
	}

	/**
	 * Merge the given (age,value) into the latest existing bin.
	 *
	 * @param x
	 * 		the x value to store (seconds since the epoch)
	 * @param y
	 * 		the y value to store
	 */
	void addToBin(double x, double y) {
		numLastBin++;
		long n = numLastBin;
		xMins[currBin] = Math.min(xMins[currBin], x);
		xMaxs[currBin] = Math.max(xMaxs[currBin], x);
		yMins[currBin] = Math.min(yMins[currBin], y);
		yMaxs[currBin] = Math.max(yMaxs[currBin], y);
		yAvgs[currBin] = yAvgs[currBin] * (n - 1) / n + y / n;
		double d = y - yAvgs[currBin];
		yVars[currBin] = yVars[currBin] * (n - 1) / n + d * d / (n - 1);
	}

	/**
	 * Create a new bin at the given index in all the arrays, holding only the given (x, y) sample.
	 *
	 * @param x
	 * 		the x value to store (seconds since the epoch)
	 * @param y
	 * 		the y value to store
	 */
	public void createBin(double x, double y) {
		// index in arrays for the new bin, right after the last bin, with wrapping
		int i = (currBin + 1) % maxBins;
		xMins[i] = x;
		xMaxs[i] = x;
		yMins[i] = y;
		yMaxs[i] = y;
		yAvgs[i] = y;
		yVars[i] = 0;
		numLastBin = 1;
		currBin = i;

		if (numBins < maxBins) { // if not full yet, then increment count
			numBins++;
		} else if (binSeconds > 0) { // if full and wrapping around
			firstBin = (firstBin + 1) % maxBins; // then the oldest must have been overwritten
		}
	}

	/**
	 * /** record the given y value, associated with an x value equal to the time right now
	 */
	public void record(double y) {
		double x = xNow();
		if (start == -1) { // remember when record() is called for the first time.
			start = x;
		}
		if (x - start < startDelay) {// don't actually record anything during the first half life.
			return;
		}
		if (numBins == 0) {
			// this is the first sample in all of history
			createBin(x, y);
		} else if (binSeconds > 0 && x < xMins[currBin] + binSeconds) {
			// Storing recent. Should stay in the current bin
			addToBin(x, y);
		} else if (binSeconds > 0) {
			// Storing recent. Should create a new bin, discarding the oldest, if necessary
			createBin(x, y);
		} else if (numLastBin < numPerBin) {
			// Storing all. The latest bin still has room for more samples
			addToBin(x, y);
		} else if (numBins + 1 < maxBins) {
			// Storing all. Need to create a new bin, and it won't fill the buffer
			createBin(x, y);
		} else {
			// Storing all. Need to create a new bin, which fills the buffer
			createBin(x, y);
			// we're now full, so merge pairs of bins to shrink to half the size
			for (int i = 0; i < numBins / 2; i++) {
				// set bin i to the merger of bin j=2*i with bin k=2*2+1
				int j = 2 * i;
				int k = 2 * i + 1;
				xMins[i] = Math.min(xMins[j], xMins[k]);
				xMaxs[i] = Math.max(xMaxs[j], xMaxs[k]);
				yMins[i] = Math.min(yMins[j], yMins[k]);
				yMaxs[i] = Math.max(yMaxs[j], yMaxs[k]);
				yAvgs[i] = (yAvgs[j] + yAvgs[k]) / 2;
				double dj = yAvgs[j] - yAvgs[i];
				double dk = yAvgs[k] - yAvgs[i];
				yVars[i] = (yVars[j] + yVars[k] + dj * dj + dk * dk) / 2;
			}
			numBins = numBins / 2;
			currBin = numBins - 1;
			numPerBin *= 2;
			numLastBin = numPerBin;
		}
	}
}

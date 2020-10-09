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

import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import static com.swirlds.common.Constants.MS_TO_NS;

/**
 * For detecting JVM pause
 * Originally written by Gil Tene of Azul Systems
 * source: https://github.com/clojure-goes-fast/jvm-hiccup-meter/blob/master/src/jvm_hiccup_meter/MeterThread.java
 */
public class JVMPauseDetectorThread extends Thread {

	private static boolean allocateObjects = true;
	public volatile Long lastSleepTimeObj; // public volatile to make sure allocs are not optimized away

	private LongConsumer callback;
	private int sleepMs;

	private volatile boolean doRun = true;

	public JVMPauseDetectorThread(LongConsumer callback, int sleepMs) {
		super("jvm-pause-detector-thread");
		this.callback = callback;
		this.sleepMs = sleepMs;
		setDaemon(true);
	}

	public void run() {
		final long sleepNs = sleepMs * MS_TO_NS;
		try {
			long shortestObservedDeltaTimeNs = Long.MAX_VALUE;
			long timeBeforeMeasurement = Long.MAX_VALUE;
			while (doRun) {
				TimeUnit.NANOSECONDS.sleep(sleepNs);
				if (allocateObjects) {
					// Allocate an object to make sure potential allocation stalls are measured.
					lastSleepTimeObj = timeBeforeMeasurement;
				}
				final long timeAfterMeasurement = System.nanoTime();
				final long deltaTimeNs = timeAfterMeasurement - timeBeforeMeasurement;
				timeBeforeMeasurement = timeAfterMeasurement;

				if (deltaTimeNs < 0) {
					// On the very first iteration (which will not time the loop in it's entirety)
					// the delta will be negative, and we'll skip recording.
					continue;
				}

				if (deltaTimeNs < shortestObservedDeltaTimeNs) {
					shortestObservedDeltaTimeNs = deltaTimeNs;
				}

				long pauseTimeNs = deltaTimeNs - shortestObservedDeltaTimeNs;
				callback.accept(pauseTimeNs / MS_TO_NS);
			}
		} catch (InterruptedException e) {
			System.err.println("JVMPauseDetectorThread terminating...");
		}
	}

	public void terminate() {
		doRun = false;
	}
}

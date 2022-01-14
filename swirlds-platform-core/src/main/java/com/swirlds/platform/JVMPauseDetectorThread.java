/*
 * (c) 2016-2022 Swirlds, Inc.
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

import static com.swirlds.common.Units.MILLISECONDS_TO_NANOSECONDS;

/**
 * For detecting JVM pause
 * Originally written by Gil Tene of Azul Systems
 * source: https://github.com/clojure-goes-fast/jvm-hiccup-meter/blob/master/src/jvm_hiccup_meter/MeterThread.java
 */
public class JVMPauseDetectorThread extends Thread {

	private static final boolean ALLOCATE_OBJECTS = true;
	public volatile Long lastSleepTimeObj; // public volatile to make sure allocs are not optimized away

	private final JvmPauseCallback callback;
	private final int sleepMs;

	private volatile boolean doRun = true;

	public JVMPauseDetectorThread(final JvmPauseCallback callback, final int sleepMs) {
		super("jvm-pause-detector-thread");
		this.callback = callback;
		this.sleepMs = sleepMs;
		setDaemon(true);
	}

	public void run() {
		final long sleepNs = (long) sleepMs * MILLISECONDS_TO_NANOSECONDS;
		try {
			long shortestObservedDeltaTimeNs = Long.MAX_VALUE;
			long timeBeforeMeasurement = Long.MAX_VALUE;
			long allocateStartNs = Long.MAX_VALUE;
			long allocateEndNs = Long.MAX_VALUE;

			while (doRun) {
				TimeUnit.NANOSECONDS.sleep(sleepNs);
				if (ALLOCATE_OBJECTS) {
					allocateStartNs = System.nanoTime();
					// Allocate an object to make sure potential allocation stalls are measured.
					lastSleepTimeObj = timeBeforeMeasurement;
					allocateEndNs = System.nanoTime();
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
				callback.pauseInfo(
						pauseTimeNs / MILLISECONDS_TO_NANOSECONDS,
						(allocateEndNs - allocateStartNs) / MILLISECONDS_TO_NANOSECONDS);
			}
		} catch (InterruptedException e) {
			System.err.println("JVMPauseDetectorThread terminating...");
			Thread.currentThread().interrupt();
		}
	}

	public void terminate() {
		doRun = false;
	}

	@FunctionalInterface
	public interface JvmPauseCallback {
		/**
		 * A callback to report pause information
		 *
		 * @param totalPauseMs
		 * 		the total pause time in milliseconds
		 * @param allocationPauseMs
		 * 		the allocation pause time in milliseconds
		 */
		void pauseInfo(long totalPauseMs, long allocationPauseMs);
	}
}

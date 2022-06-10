/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.threading;

import com.swirlds.common.threading.framework.StoppableThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.THREADS;

/**
 * Utility class for performing common actions with threads.
 */
public final class ThreadUtils {

	private static final Logger LOG = LogManager.getLogger();

	// Prevent instantiation of a static utility class
	private ThreadUtils() {

	}

	/**
	 * Stop the provided threads, and block until they have all stopped.
	 *
	 * @param threadsToStop
	 * 		an array of queue threads to stop. Must not be null.
	 */
	public static void stopThreads(final StoppableThread... threadsToStop) throws InterruptedException {
		LOG.info(THREADS.getMarker(), "{} thread(s) will be terminated", threadsToStop.length);
		for (final StoppableThread thread : threadsToStop) {
			if (thread != null) {
				LOG.info(THREADS.getMarker(), "stopping thread {}", thread.getName());
				thread.stop();
			}
		}

		for (final StoppableThread thread : threadsToStop) {
			if (thread != null) {
				LOG.info(THREADS.getMarker(), "joining thread {}", thread.getName());
				thread.join();
			}
		}
		LOG.info(THREADS.getMarker(), "{} thread(s) terminated", threadsToStop.length);
	}

}

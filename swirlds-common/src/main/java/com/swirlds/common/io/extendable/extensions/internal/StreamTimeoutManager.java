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

package com.swirlds.common.io.extendable.extensions.internal;

import com.swirlds.common.io.extendable.extensions.TimeoutStreamExtension;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class is responsible for monitoring instances of {@link TimeoutStreamExtension}.
 */
public final class StreamTimeoutManager {

	/**
	 * The number of times to check per second.
	 */
	private static final double RATE = 10.0;

	/**
	 * Timeout extensions that are currently being tracked.
	 */
	private static final Queue<TimeoutStreamExtension> extensions = new ConcurrentLinkedQueue<>();

	/**
	 * True if the thread has started, otherwise false.
	 */
	private static boolean started = false;

	/**
	 * The thread that monitors the timeout extensions.
	 */
	private static final StoppableThread thread = new StoppableThreadConfiguration<>()
			.setComponent("timeout-extension")
			.setThreadName("manager")
			.setMaximumRate(RATE)
			.setWork(StreamTimeoutManager::doWork)
			.build();

	private StreamTimeoutManager() {

	}

	/**
	 * Runs in a loop, checks if any timeout extensions have exceeded limits.
	 */
	private static void doWork() {
		for (final TimeoutStreamExtension extension : extensions) {
			// This method checks if the stream has timed out, and closes the stream if it has timed out.
			// This method returns true if the extension is still open, and false when it has closed.
			// Once the extension has closed we should deregister the extension.
			if (!extension.checkTimeout()) {
				deregister(extension);
			}
		}
	}

	/**
	 * Start the thread if it hasn't yet been started.
	 */
	private static synchronized void start() {
		if (!started) {
			thread.start();
			started = true;
		}
	}

	/**
	 * Register a new extension.
	 *
	 * @param extension
	 * 		the extension to register
	 */
	public static void register(final TimeoutStreamExtension extension) {
		start();
		extensions.add(extension);
	}

	/**
	 * De-register an extension that has been closed.
	 *
	 * @param extension
	 * 		the extension that was closed
	 */
	private static void deregister(final TimeoutStreamExtension extension) {
		final Iterator<TimeoutStreamExtension> iterator = extensions.iterator();
		while (iterator.hasNext()) {
			if (iterator.next() == extension) {
				iterator.remove();
				return;
			}
		}
	}

	/**
	 * Utility method. Get the current number of timeout stream extensions that are currently being monitored
	 * by the background thread.
	 *
	 * @return the number of monitored streams
	 */
	public static int getMonitoredStreamCount() {
		return extensions.size();
	}
}

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

package com.swirlds.common;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This utility class provides methods for reading the time that a node was started.
 */
public final class StartupTime {

	private static final AtomicReference<Instant> startupTime = new AtomicReference<>(null);

	private StartupTime() {

	}

	/**
	 * This method must be called when a node originally starts. Must be called prior to spawning of threads
	 * that may read startup time. If called multiple times (for example, if there are multiple local nodes)
	 * then only the first call will assign the startup time.
	 */
	public static void markStartupTime() {
		startupTime.compareAndSet(null, Instant.now());
	}

	/**
	 * Get the time that this node was started. Exact instant is captured at some time when the JVM is starting.
	 *
	 * @return the time when this node started
	 */
	public static Instant getStartupTime() {
		final Instant time = startupTime.get();
		if (time == null) {
			throw new IllegalStateException("startup time not marked");
		}
		return time;
	}

	/**
	 * Get the time since this node was started. Exact instant is captured at some time when the JVM is starting.
	 *
	 * @return the time since when this node started
	 */
	public static Duration getTimeSinceStartup() {
		return Duration.between(getStartupTime(), Instant.now());
	}
}

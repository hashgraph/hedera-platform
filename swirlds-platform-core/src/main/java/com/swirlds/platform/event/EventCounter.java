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

package com.swirlds.platform.event;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A class that tracks the number of events in memory
 */
public class EventCounter {
	/**
	 * Number of events currently in memory, for all instantiated Platforms put together. This is only used
	 * for an internal statistic, not for any of the algorithms.
	 */
	private static final AtomicLong numEventsInMemory = new AtomicLong(0);

	/**
	 * Number of events currently in memory, for all instantiated Platforms put together.
	 *
	 * @return long value for number of events.
	 */
	public static long getNumEventsInMemory() {
		return numEventsInMemory.get();
	}

	/**
	 * Called when an event is created
	 */
	public static void eventCreated() {
		numEventsInMemory.incrementAndGet();
	}

	/**
	 * Called when an event is cleared, to decrement the count of how many uncleared events are in memory
	 */
	public static void eventCleared() {
		numEventsInMemory.decrementAndGet();
	}
}

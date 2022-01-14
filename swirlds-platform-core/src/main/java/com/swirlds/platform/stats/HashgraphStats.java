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

package com.swirlds.platform.stats;

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.observers.StaleEventObserver;


public interface HashgraphStats extends DefaultStats, StaleEventObserver {
	/**
	 * Update a statistics accumulator whenever this node creates an event with
	 * an other-parent that has no children. (The OP is "rescued".)
	 */
	void rescuedEvent();

	/**
	 * Update a statistics accumulator when a duplicate event has been detected.
	 */
	void duplicateEvent();

	/**
	 * Update a statistics accumulator when a non-duplicate event has been detected
	 */
	void nonDuplicateEvent();

	/**
	 * Update a statistics accumulator when a gossiped event has been received.
	 *
	 * @param event
	 * 		an event
	 */
	void receivedEvent(EventImpl event);

	/**
	 * Update a statistics accumulator when a bad event is detected.
	 */
	void invalidEventSignature();

	/**
	 * Update event task statistics
	 * @param startTime a start time, in nanoseconds
	 */
	void processedEventTask(long startTime);

	/**
	 * Notifies the stats that the event creation phase has entered
	 *
	 * @param shouldCreateEvent
	 * 		did the sync manager tell us to create an event?
	 */
	void eventCreation(boolean shouldCreateEvent);
}

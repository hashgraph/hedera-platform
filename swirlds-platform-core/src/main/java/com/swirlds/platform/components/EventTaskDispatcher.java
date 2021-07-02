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

package com.swirlds.platform.components;

import com.swirlds.platform.EventValidator;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.ValidateEventTask;
import com.swirlds.platform.stats.HashgraphStats;

/**
 * This class is responsible for dispatching tasks to the {@link EventCreator} and {@link EventValidator}.
 */
public class EventTaskDispatcher {

	/**
	 * An {@link EventValidator}
	 */
	private final EventValidator eventValidator;

	/**
	 * Responsible for creating all new events that originate on this node.
	 */
	private final EventCreator eventCreator;

	private final HashgraphStats stats;

	/**
	 * Constructor
	 *
	 * @param eventValidator
	 * 		an event validator
	 * @param eventCreator
	 * 		an event creator
	 */
	public EventTaskDispatcher(
			EventValidator eventValidator,
			EventCreator eventCreator,
			HashgraphStats stats) {
		this.eventValidator = eventValidator;
		this.eventCreator = eventCreator;
		this.stats = stats;
	}

	/**
	 * Dispatch a single {@code EventIntakeTask} instance. This routine validates
	 * the given task and adds it to the hashgraph via {@code EventIntake.addEvent}
	 *
	 * @param eventIntakeTask
	 * 		A task to be dispatched
	 */
	public void dispatchTask(EventIntakeTask eventIntakeTask) {
		long start = stats.time();
		//Moving validation to inline model
		if (eventIntakeTask instanceof CreateEventTask) {
			createNewEvent((CreateEventTask) eventIntakeTask);
		} else {
			validateEvent((ValidateEventTask) eventIntakeTask);
		}
		stats.processedEventTask(start);
	}

	/**
	 * Creates a new event based on the information supplied
	 *
	 * @param createEventTask
	 * 		the information used to create an event
	 */
	private void createNewEvent(final CreateEventTask createEventTask) {
		eventCreator.createEvent(createEventTask.getOtherId());
	}

	/**
	 * Validates the given event information
	 *
	 * @param validateEventTask
	 * 		the information used to create an event
	 */
	private void validateEvent(final ValidateEventTask validateEventTask) {
		eventValidator.validateEvent(validateEventTask.getHashedData(), validateEventTask.getUnhashedData());
	}
}

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

package com.swirlds.platform.components;

import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.ValidEvent;
import com.swirlds.platform.stats.EventIntakeStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * This class is responsible for dispatching tasks to the {@link EventCreator} and {@link EventValidator}.
 */
public class EventTaskDispatcher {
	private static final Logger LOG = LogManager.getLogger();

	/** An {@link EventValidator} */
	private final EventValidator eventValidator;

	/** Responsible for creating all new events that originate on this node */
	private final EventCreator eventCreator;

	/** Handles events that are known to be valid */
	private final Consumer<EventImpl> validEventHandler;

	/**
	 * A statistics accumulator for hashgraph-related quantities, used here to record
	 * time to taken to process a task to hashgraph event
	 */
	private final EventIntakeStats stats;

	/**
	 * Constructor
	 *
	 * @param eventValidator
	 * 		an event validator
	 * @param eventCreator
	 * 		an event creator
	 * @param validEventHandler
	 * 		handles already validated events
	 * @param stats
	 * 		dispatching statistics
	 */
	public EventTaskDispatcher(
			final EventValidator eventValidator,
			final EventCreator eventCreator,
			final Consumer<EventImpl> validEventHandler,
			final EventIntakeStats stats) {
		this.eventValidator = eventValidator;
		this.eventCreator = eventCreator;
		this.validEventHandler = validEventHandler;
		this.stats = stats;
	}

	/**
	 * Dispatch a single {@code EventIntakeTask} instance. This routine validates
	 * the given task and adds it to the hashgraph via {@code EventIntake.addEvent}
	 *
	 * @param eventIntakeTask
	 * 		A task to be dispatched
	 */
	public void dispatchTask(final EventIntakeTask eventIntakeTask) {
		final long start = stats.time();
		//Moving validation to inline model
		if (eventIntakeTask instanceof GossipEvent task) {
			eventValidator.validateEvent(task);
		} else if (eventIntakeTask instanceof ValidEvent task) {
			validEventHandler.accept(task.event());
		} else if (eventIntakeTask instanceof CreateEventTask task) {
			eventCreator.createEvent(task.getOtherId());
		} else {
			LOG.error(LogMarker.EXCEPTION.getMarker(), "Unknown instance type: {}", eventIntakeTask.getClass());
		}
		stats.processedEventTask(start);
	}
}

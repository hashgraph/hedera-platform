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

import com.swirlds.platform.event.EventStringBuilder;
import com.swirlds.platform.event.ValidateEventTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.ERROR;

/**
 * A collection of methods for creating strings from events events.
 */
public final class EventStrings {
	private static final Logger LOG = LogManager.getLogger();

	private EventStrings() {

	}
	/**
	 * A string representation of an event in the following format:
	 * {@code (creatorID, generation, shortHash)}
	 *
	 * @param event
	 * 		the event to convert to a string
	 * @return A short string representation of an event
	 */
	public static String toShortString(EventImpl event) {
		return EventStringBuilder.builder(event).appendEvent().build();
	}

	/**
	 * Same as {@link #toShortString(EventImpl)}
	 */
	public static String toShortString(ValidateEventTask event) {
		return EventStringBuilder.builder(event).appendEvent().build();
	}

	/**
	 * A string representation of an event in the following format:
	 * {@code (creatorID, generation, shortHash)
	 * sp(creatorID, selfParentGeneration, selfParentShortHash)
	 * op(otherParentID, otherParentGeneration, otherParentShortHash)}
	 *
	 * @param event
	 * 		the event to convert to a string
	 * @return A medium string representation of an event
	 */
	public static String toMediumString(EventImpl event) {
		return EventStringBuilder.builder(event).appendEvent().appendSelfParent().appendOtherParent().build();
	}

	/**
	 * Same as {@link #toMediumString(EventImpl)}
	 */
	public static String toMediumString(ValidateEventTask event) {
		return EventStringBuilder.builder(event).appendEvent().appendSelfParent().appendOtherParent().build();
	}


	/**
	 * Dump information about an array of events. Useful for debugging event issues.
	 */
	public static void dumpEventInfo(final EventImpl[] events) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Event dump: ");

		for (final EventImpl event : events) {
			sb.append(toMediumString(event));
			sb.append("\n");
		}

		LOG.error(ERROR.getMarker(), sb);
	}

}

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

package com.swirlds.platform;

import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.event.EventStringBuilder;

/**
 * A collection of methods for creating strings from events events.
 */
public final class EventStrings {
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
	public static String toShortString(final EventImpl event) {
		return EventStringBuilder.builder(event).appendEvent().build();
	}

	/**
	 * Same as {@link #toShortString(EventImpl)}
	 */
	public static String toShortString(final BaseEvent event) {
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
	public static String toMediumString(final EventImpl event) {
		return EventStringBuilder.builder(event).appendEvent().appendSelfParent().appendOtherParent().build();
	}

	/**
	 * Same as {@link #toMediumString(EventImpl)}
	 */
	public static String toMediumString(final BaseEvent event) {
		return EventStringBuilder.builder(event).appendEvent().appendSelfParent().appendOtherParent().build();
	}
}

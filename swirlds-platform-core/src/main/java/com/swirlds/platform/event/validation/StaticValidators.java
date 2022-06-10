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

package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.consensus.GraphGenerations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * A collection of static methods for validating events
 */
public final class StaticValidators {
	private static final Logger LOG = LogManager.getLogger();

	private StaticValidators() {
	}

	/**
	 * Determine whether a given event has a valid creation time.
	 *
	 * @param event
	 * 		the event to be validated
	 * @return true iff the creation time of the event is strictly after the
	 * 		creation time of its self-parent
	 */
	public static boolean isValidTimeCreated(final EventImpl event) {
		if (event.getSelfParent() != null) {

			final EventImpl selfParent = event.getSelfParent();
			if (selfParent != null && !event.getTimeCreated().isAfter(selfParent.getTimeCreated())) {

				LOG.debug(INVALID_EVENT_ERROR.getMarker(), () -> String.format(
						"Event timeCreated ERROR event %s created:%s, parent created:%s",
						event.toMediumString(),
						event.getTimeCreated().toString(),
						selfParent.getTimeCreated().toString()));
				return false;
			}
		}

		return true;
	}

	/**
	 * Validates if an event's parent data is correct
	 *
	 * @param event
	 * 		the event to validate
	 * @return true if the event is valid, false otherwise
	 */
	public static boolean isParentDataValid(final BaseEvent event) {
		final BaseEventHashedData hashedData = event.getHashedData();
		final Hash spHash = hashedData.getSelfParentHash();
		final Hash opHash = hashedData.getOtherParentHash();
		final boolean hasSpHash = spHash != null;
		final boolean hasOpHash = opHash != null;
		final boolean hasSpGen = hashedData.getSelfParentGen() >= GraphGenerations.FIRST_GENERATION;
		final boolean hasOpGen = hashedData.getOtherParentGen() >= GraphGenerations.FIRST_GENERATION;

		if (hasSpGen != hasSpHash) {
			LOG.error(INVALID_EVENT_ERROR.getMarker(), "invalid self-parent: {} ", event::toString);
			return false;
		}
		if (hasOpGen != hasOpHash) {
			LOG.error(INVALID_EVENT_ERROR.getMarker(), "invalid other-parent: {} ", event::toString);
			return false;
		}
		if (hasSpHash && hasOpHash && spHash.equals(opHash)) {
			LOG.error(INVALID_EVENT_ERROR.getMarker(), "both parents have the same hash: {} ", event::toString);
			return false;
		}
		return true;
	}
}

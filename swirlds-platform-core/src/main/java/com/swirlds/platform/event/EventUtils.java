/*
 * (c) 2016-2020 Swirlds, Inc.
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

import com.swirlds.platform.EventImpl;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class EventUtils {
	/**
	 * A method that does a XOR on all the event hashes in an array
	 *
	 * @param events
	 * 		the events whose hashes are to be XORed
	 * @return XOR of the event hashes, or null if there are no events
	 */
	public static byte[] xorEventHashes(EventImpl[] events) {
		if (events == null || events.length == 0) {
			return null;
		}
		byte[] xor = new byte[events[0].getBaseHash().getValue().length];
		for (EventImpl event : events) {
			for (int j = 0; j < xor.length; j++) {
				xor[j] = (byte) (xor[j] ^ event.getBaseHash().getValue()[j]);
			}
		}
		return xor;
	}

	/**
	 * Converts the event to a short string based on it's creator ID and sequence number. Internally this uses {@link
	 * EventImpl#getCreatorSeqPair()}.
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string of the form "(creatorId, creatorSeq)"
	 */
	public static String toShortString(EventImpl event) {
		return (event != null) ? event.getCreatorSeqPair().toString() : "null";
	}

	/**
	 * convert the event to a longer string, based on the creator ID and sequence number, of it and its other parent.
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string of the form "(creatorId, creatorSeq, otherId, otherSeq)"
	 */
	public static String toLongerString(EventImpl event) {
		if (event == null) {
			return "null";
		}
		return "("
				+ event.getBaseEventHashedData().getCreatorId() + ","
				+ event.getBaseEventUnhashedData().getCreatorSeq() + ","
				+ event.getBaseEventUnhashedData().getOtherId() + ","
				+ event.getBaseEventUnhashedData().getOtherSeq() + ")";
	}

	/**
	 * Convert an array of events to a single string, using toShortString() on each, and separating with commas.
	 *
	 * @param events
	 * 		array of events to convert
	 * @return a single string with a comma separated list of all of the event strings
	 */
	public static String toShortStrings(EventImpl[] events) {
		if (events == null) {
			return "null";
		}
		return Arrays.stream(events).map(EventUtils::toShortString).collect(
				Collectors.joining(","));
	}

	public static String toShortStrings(Iterable<EventImpl> events) {
		if (events == null) {
			return "null";
		}
		return StreamSupport.stream(events.spliterator(), false)
				.map(EventUtils::toShortString)
				.collect(Collectors.joining(","));
	}
}

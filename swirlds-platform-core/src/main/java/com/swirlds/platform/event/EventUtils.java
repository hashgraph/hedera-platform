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

package com.swirlds.platform.event;

import com.swirlds.common.events.Event;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public abstract class EventUtils {
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * A method that does a XOR on all the event hashes in an array
	 *
	 * @param events
	 * 		the events whose hashes are to be XORed
	 * @return XOR of the event hashes, or null if there are no events
	 */
	public static byte[] xorEventHashes(final EventImpl[] events) {
		if (events == null || events.length == 0) {
			return null;
		}
		final byte[] xor = new byte[events[0].getBaseHash().getValue().length];
		for (final EventImpl event : events) {
			for (int j = 0; j < xor.length; j++) {
				xor[j] = (byte) (xor[j] ^ event.getBaseHash().getValue()[j]);
			}
		}
		return xor;
	}

	/**
	 * Converts the event to a short string. Should be replaced by {@link EventStrings#toShortString(EventImpl)}
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string
	 */
	public static String toShortString(final EventImpl event) {
		return EventStrings.toShortString(event);
	}

	/**
	 * Convert an array of events to a single string, using toShortString() on each, and separating with commas.
	 *
	 * @param events
	 * 		array of events to convert
	 * @return a single string with a comma separated list of all of the event strings
	 */
	public static String toShortStrings(final EventImpl[] events) {
		if (events == null) {
			return "null";
		}
		return Arrays.stream(events).map(EventUtils::toShortString).collect(
				Collectors.joining(","));
	}

	public static String toShortStrings(final Iterable<EventImpl> events) {
		if (events == null) {
			return "null";
		}
		return StreamSupport.stream(events.spliterator(), false)
				.map(EventUtils::toShortString)
				.collect(Collectors.joining(","));
	}

	public static int generationComparator(final Event e1, final Event e2) {
		return Long.compare(e1.getGeneration(), e2.getGeneration());
	}

	/**
	 * Prepares consensus events for shadow graph during a restart or reconnect by sorting the events by generation and
	 * checking for generation gaps.
	 *
	 * @param events
	 * 		events supplied by consensus
	 * @return a list of input events, sorted and checked
	 */
	public static List<EventImpl> prepareForShadowGraph(final EventImpl[] events) {
		if (events == null || events.length == 0) {
			return Collections.emptyList();
		}
		// shadow graph expects them to be sorted
		Arrays.sort(events, EventUtils::generationComparator);
		final List<EventImpl> forShadowGraph = Arrays.asList(events);
		try {
			checkForGenerationGaps(forShadowGraph);
		} catch (final IllegalArgumentException e) {
			LOG.error(LogMarker.EXCEPTION.getMarker(),
					"Issue found when checking event to provide to the Shadowgraph." +
							"This issue might not be fatal, so loading of the state will proceed.", e);
		}

		return forShadowGraph;
	}

	/**
	 * Checks if there is a generation difference of more than 1 between events, if there is, throws an exception
	 *
	 * @param events
	 * 		events to look for generation gaps in, sorted in ascending order by generation
	 * @throws IllegalArgumentException
	 * 		if any problem is found with the signed state events
	 */
	public static void checkForGenerationGaps(final List<EventImpl> events) {
		if (events == null || events.isEmpty()) {
			throw new IllegalArgumentException("Signed state events list should not be null or empty");
		}
		final ListIterator<EventImpl> listIterator = events.listIterator(events.size());

		// Iterate through the list from back to front, evaluating the youngest events first
		EventImpl prev = listIterator.previous();

		while (listIterator.hasPrevious()) {
			final EventImpl event = listIterator.previous();
			final long diff = prev.getGeneration() - event.getGeneration();
			if (diff > 1 || diff < 0) {
				// There is a gap in generations
				throw new IllegalArgumentException(
						String.format("Found gap between %s and %s", event.toMediumString(), prev.toMediumString())
				);
			}

			prev = event;
		}
	}

	/**
	 * Creates an event comparator using consensus time. If the event does not have a consensus time, the estimated time
	 * is used instead.
	 *
	 * @return the comparator
	 */
	public static int consensusPriorityComparator(final EventImpl x, final EventImpl y) {
		if (x == null || y == null) {
			return 0;
		}
		final Instant xTime = x.getConsensusTimestamp() == null ? x.getEstimatedTime() : x.getConsensusTimestamp();
		final Instant yTime = y.getConsensusTimestamp() == null ? y.getEstimatedTime() : y.getConsensusTimestamp();
		if (xTime == null || yTime == null) {
			return 0;
		}
		return xTime.compareTo(yTime);
	}

}

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

package com.swirlds.platform.event;

import com.swirlds.common.events.Event;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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
	 * Converts the event to a short string. Should be replaced by {@link EventStrings#toShortString(EventImpl)}
	 *
	 * @param event
	 * 		the event to convert
	 * @return a short string
	 */
	public static String toShortString(EventImpl event) {
		return EventStrings.toShortString(event);
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
		return lastContiguousEvents(forShadowGraph);
	}

	/**
	 * <p>Returns the subset of events that are in the latest (youngest) contiguous set of generations. If there is a
	 * generation difference of more than 1 between events, all events older than the gap are excluded from the returned
	 * list.</p>
	 *
	 * <p>{@code events} must be ordered by generation in ascending order (smallest to largest). The resulting list
	 * maintains the order of {@code events}.</p>
	 *
	 * @param events
	 * 		events to look for generation gaps in, sorted in ascending order by generation
	 * @return the list of events that are the subset of {@code events} that are in the latest contiguous set of
	 * 		generations.
	 */
	public static List<EventImpl> lastContiguousEvents(final List<EventImpl> events) {
		if (events == null) {
			throw new IllegalArgumentException("Event list should not be null.");
		}
		if (events.isEmpty()) {
			return Collections.emptyList();
		}
		LinkedList<EventImpl> lastContiguousEvents = new LinkedList<>();
		ListIterator<EventImpl> listIterator = events.listIterator(events.size());

		// Iterate through the list from back to front, evaluating the youngest events first
		EventImpl prev = listIterator.previous();

		while (listIterator.hasPrevious()) {

			// No gap found yet, so add the previous event.
			// Add to the front to maintain the original order.
			lastContiguousEvents.addFirst(prev);

			EventImpl event = listIterator.previous();
			final long diff = prev.getGeneration() - event.getGeneration();
			if (diff > 1 || diff < 0) {
				// There is a gap in generations. We don't include any
				// events lower than the gap, so stop iterating.
				break;
			}

			prev = event;
		}

		// If no gaps were found, prev will be the first event in the list,
		// in which case it should be included because there were no gaps
		if (prev == events.get(0)) {
			lastContiguousEvents.addFirst(prev);
		}
		return lastContiguousEvents;
	}

}

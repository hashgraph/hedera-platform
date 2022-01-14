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

package com.swirlds.platform.components;

import com.swirlds.common.NodeId;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.observers.EventAddedObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * This data structure is used to track the most recent events from each node.
 */
public class EventMapper implements EventAddedObserver {

	private static final Logger log = LogManager.getLogger();

	/**
	 * Contains the most recent event from each node.
	 */
	private final Map<Long, EventImpl> events;

	/**
	 * Tracks if the most recent event from each node has at least one child
	 */
	private final Map<Long, Boolean> hasChild;

	/**
	 * Tracks if the most recent event from each node has a descendant which is created by this node.
	 */
	private final Map<Long, Boolean> mostRecentEventUsedAsOtherParent;

	/**
	 * The number of nodes in this node's initial address book
	 */
	private final int nodeNumber;

	/**
	 * The ID of this node
	 */
	private final NodeId selfId;

	/**
	 * The maximum generation of all the events we have.
	 */
	private long maxGeneration;

	/**
	 * Constructor
	 *
	 * @param selfId
	 * 		this node's {@link NodeId}
	 * @param nodeNumber
	 * 		the size of this node's initial address book
	 */
	public EventMapper(NodeId selfId, int nodeNumber) {
		this.selfId = selfId;
		this.nodeNumber = nodeNumber;

		events = new HashMap<>();
		hasChild = new HashMap<>();
		mostRecentEventUsedAsOtherParent = new HashMap<>();
		maxGeneration = -1;
	}

	/**
	 * Dump the contents of a map to a string builder.
	 */
	private static void dumpMap(final StringBuilder sb, final String mapName, final Map<Long, ?> map) {
		sb.append(mapName).append(":\n");
		final List<Map.Entry<Long, ?>> sortedEntries = new ArrayList<>(map.entrySet());
		Collections.sort(sortedEntries, (entry1, entry2) -> (int) (entry1.getKey() - entry2.getKey()));
		for (final Map.Entry<Long, ?> entry : sortedEntries) {
			sb.append("   - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}
	}

	/**
	 * This method is called in the event of an event mapper error. Writes data
	 * from the event mapper to the log.
	 */
	private void dumpEventMapperData() {
		final StringBuilder sb = new StringBuilder();
		sb.append("EventMapper dump: number of nodes = ")
				.append(nodeNumber).append(", self ID = ").append(selfId).append("\n");
		dumpMap(sb, "events", events);
		dumpMap(sb, "hasChild", hasChild);
		dumpMap(sb, "mostRecentEventUsedAsOtherParent", mostRecentEventUsedAsOtherParent);
		log.error(EXCEPTION.getMarker(), sb);
	}

	/**
	 * Notifies the mapper that an event has been added
	 *
	 * @param event
	 * 		the event that was added
	 */
	@Override
	public synchronized void eventAdded(EventImpl event) {

		final long nodeId = event.getCreatorId();

		if (getSequenceNumber(nodeId) > event.getSeq()) {
			dumpEventMapperData();
			throw new IllegalStateException("Event has been added to the mapper in the wrong order");
		}

		events.put(nodeId, event);
		maxGeneration = Math.max(maxGeneration, event.getGeneration());

		final EventImpl otherParent = event.getOtherParent();
		if (otherParent != null) {
			if (otherParent.getSeq() == getSequenceNumber(otherParent.getCreatorId())) {
				// The event that was just added has the most recent event from otherParentId as an ancestor.
				hasChild.put(otherParent.getCreatorId(), true);
				if (event.isCreatedBy(selfId)) {
					mostRecentEventUsedAsOtherParent.put(otherParent.getCreatorId(), true);
				}
			} else if (otherParent.getSeq() > getSequenceNumber(otherParent.getCreatorId())) {
				dumpEventMapperData();
				throw new IllegalStateException("Other parent has not yet been added to the event mapper");
			}
		}

		// The most recent event added can't, by definition, have any descendants yet.
		hasChild.put(nodeId, false);
		mostRecentEventUsedAsOtherParent.put(nodeId, false);
	}

	/**
	 * Reset this instance to its constructed state
	 */
	public synchronized void clear() {
		events.clear();
		hasChild.clear();
		mostRecentEventUsedAsOtherParent.clear();
		maxGeneration = -1;
	}

	/**
	 * Get the most recent event from a given node, or null if no such event exists.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized EventImpl getMostRecentEvent(long nodeId) {
		return events.get(nodeId);
	}

	/**
	 * Get the sequence number of the most recent event from a given node, or -1 if there is no event from that node.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized long getSequenceNumber(long nodeId) {
		final EventImpl event = events.get(nodeId);
		if (event == null) {
			return -1;
		}
		return event.getSeq();
	}

	/**
	 * Check if the most recent event created by a given node has been used as an other parent by an event
	 * created by this node.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized boolean hasMostRecentEventBeenUsedAsOtherParent(long nodeId) {
		return mostRecentEventUsedAsOtherParent.getOrDefault(nodeId, false);
	}

	/**
	 * Return the sequence numbers of the last event created by each member. This is a copy, so it is OK for
	 * the caller to modify it.
	 *
	 * The returned array values may be slightly out of date, and different elements of the array may be out
	 * of date by different amounts.
	 *
	 * @return an array of sequence numbers indexed by member id number
	 * @deprecated this method utilizes node IDs as array indices
	 */
	@Deprecated
	public synchronized long[] getLastSeqByCreator() {
		long[] result = new long[nodeNumber];
		for (int i = 0; i < nodeNumber; i++) {
			result[i] = getSequenceNumber(i);
		}
		return result;
	}

	/**
	 * Check for duplicate event in the hashgraph by creator ID and sequence number.
	 *
	 * @param event
	 * 		an event
	 * @return true iff the given event is a duplicate of a known event
	 */
	public synchronized boolean isDuplicateInHashgraph(final EventImpl event) {
		final EventImpl mapped = events.get(event.getCreatorId());
		final long mappedSeq = mapped == null ? EventConstants.SEQUENCE_UNDEFINED : mapped.getSeq();
		if (mapped != null && mappedSeq == event.getSeq() && mapped.getGeneration() != event.getGeneration()) {
			log.error(INVALID_EVENT_ERROR.getMarker(),
					"Detected two events with the same sequence number {}\n{} & {}",
					() -> mappedSeq, mapped::toMediumString, event::toMediumString);
		}
		return event.getSeq() <= mappedSeq;
	}

	/**
	 * Check if the most recent event from a given node has any descendants.
	 *
	 * @param nodeId
	 * 		the node ID in question
	 * @return true if the most recent event has descendants, otherwise false.
	 * 		False if there are no events for the given node ID.
	 */
	public synchronized boolean doesMostRecentEventHaveDescendants(final long nodeId) {
		return hasChild.getOrDefault(nodeId, false);
	}

	/**
	 * @return the highest generation of all events we know about
	 */
	public synchronized long getMaxGeneration() {
		return maxGeneration;
	}
}

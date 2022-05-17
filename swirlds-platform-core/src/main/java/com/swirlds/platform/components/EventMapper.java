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

import com.swirlds.common.NodeId;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.SelfEventStorage;
import com.swirlds.platform.observers.EventAddedObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This data structure is used to track the most recent events from each node. This data structure will track the most
 * recent event added and will not check the ordering of these events. If a fork exists, it will track whichever fork is
 * added last.
 */
public class EventMapper implements EventAddedObserver, SelfEventStorage {
	private static final EventMapping DEFAULT_RETURN = new EventMapping(null);
	/**
	 * Contains the most recent event added from each node, with information about its descendants
	 */
	private final Map<Long, EventMapping> mappings;

	/**
	 * The ID of this node
	 */
	private final NodeId selfId;

	/**
	 * Constructor
	 *
	 * @param selfId
	 * 		this node's {@link NodeId}
	 */
	public EventMapper(final NodeId selfId) {
		this.selfId = selfId;
		mappings = new HashMap<>();
	}

	/**
	 * Notifies the mapper that an event has been added
	 *
	 * @param event
	 * 		the event that was added
	 */
	@Override
	public synchronized void eventAdded(final EventImpl event) {
		final long nodeId = event.getCreatorId();
		mappings.put(nodeId, new EventMapping(event));

		final EventImpl otherParent = event.getOtherParent();
		if (otherParent == null) {
			return;
		}
		final EventMapping parentMapping = mappings.get(otherParent.getCreatorId());
		if (parentMapping == null || !parentMapping.getEvent().getBaseHash().equals(otherParent.getBaseHash())) {
			// if the other parent is not an event we are tracking, then there is nothing to do
			return;
		}

		// we now know this event has a descendant
		parentMapping.setHasDescendant(true);
		if (event.isCreatedBy(selfId)) {
			// if we have created the event added, then the parent has a direct self descendant
			parentMapping.setHasDirectSelfDescendant(true);
		}
	}

	/**
	 * Reset this instance to its constructed state
	 */
	public synchronized void clear() {
		mappings.clear();
	}

	/**
	 * Get the most recent event from a given node, or null if no such event exists.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized EventImpl getMostRecentEvent(final long nodeId) {
		return mappings.getOrDefault(nodeId, DEFAULT_RETURN).getEvent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized EventImpl getMostRecentSelfEvent() {
		return getMostRecentEvent(selfId.getId());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void setMostRecentSelfEvent(final EventImpl selfEvent) {
		// does nothing, self events will be added through the eventAdded() method
	}

	/**
	 * Get the generation number of the most recent event from a given node,
	 * or {@link EventConstants#GENERATION_UNDEFINED} if there is no event from that node.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized long getHighestGenerationNumber(final long nodeId) {
		final EventMapping mapping = mappings.get(nodeId);
		if (mapping == null) {
			return EventConstants.GENERATION_UNDEFINED;
		}
		return mapping.getEvent().getGeneration();
	}

	/**
	 * Check if the most recent event created by a given node has been used as an other parent by an event
	 * created by this node.
	 *
	 * @param nodeId
	 * 		the ID of the node in question
	 */
	public synchronized boolean hasMostRecentEventBeenUsedAsOtherParent(final long nodeId) {
		return mappings.getOrDefault(nodeId, DEFAULT_RETURN).isHasDirectSelfDescendant();
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
		return mappings.getOrDefault(nodeId, DEFAULT_RETURN).isHasDescendant();
	}

	/**
	 * @return a list of the most recently added events by each creator
	 */
	public synchronized List<EventImpl> getMostRecentEventsByEachCreator() {
		return mappings.values().stream().map(EventMapping::getEvent).collect(Collectors.toList());
	}

	private static class EventMapping {
		private final EventImpl event;
		private boolean hasDirectSelfDescendant;
		private boolean hasDescendant;

		public EventMapping(final EventImpl event) {
			this.event = event;
			// The most recent event added can't, by definition, have any descendants yet.
			hasDescendant = false;
			hasDirectSelfDescendant = false;
		}

		public EventImpl getEvent() {
			return event;
		}

		public boolean isHasDirectSelfDescendant() {
			return hasDirectSelfDescendant;
		}

		public boolean isHasDescendant() {
			return hasDescendant;
		}

		public void setHasDirectSelfDescendant(boolean hasDirectSelfDescendant) {
			this.hasDirectSelfDescendant = hasDirectSelfDescendant;
		}

		public void setHasDescendant(boolean hasDescendant) {
			this.hasDescendant = hasDescendant;
		}
	}
}

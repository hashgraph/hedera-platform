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

package com.swirlds.platform.event.linking;

import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.sync.SyncGenerations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * An {@link EventLinker} which expects events to be provided in topological order. If an out-of-order event is provided,
 * it is logged and discarded.
 */
public class InOrderLinker implements EventLinker {
	private static final Logger LOG = LogManager.getLogger();
	private final ParentFinder parentFinder;
	/** Provides the most recent event by the supplied creator ID */
	private final Function<Long, EventImpl> mostRecentEvent;
	/** The current consensus generations */
	private GraphGenerations currentGenerations = SyncGenerations.GENESIS_GENERATIONS;
	private EventImpl linkedEvent = null;

	public InOrderLinker(final ParentFinder parentFinder, final Function<Long, EventImpl> mostRecentEvent) {
		this.parentFinder = parentFinder;
		this.mostRecentEvent = mostRecentEvent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void linkEvent(final GossipEvent event) {
		final ChildEvent childEvent = parentFinder.findParents(
				event,
				currentGenerations.getMinGenerationNonAncient()
		);
		if (childEvent.isOrphan()) {
			logMissingParents(childEvent);
			childEvent.orphanForever();
			return;
		}
		if (linkedEvent != null) {
			LOG.error(LogMarker.EXCEPTION.getMarker(),
					"Unprocessed linked event: {}",
					() -> EventStrings.toMediumString(linkedEvent));
			linkedEvent.clear();
		}
		linkedEvent = childEvent.getChild();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateGenerations(final GraphGenerations generations) {
		currentGenerations = generations;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasLinkedEvents() {
		return linkedEvent != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventImpl pollLinkedEvent() {
		final EventImpl tmp = linkedEvent;
		linkedEvent = null;
		return tmp;
	}

	private void logMissingParents(final ChildEvent event) {
		final GossipEvent e = event.getChild().getBaseEvent();
		LOG.error(INVALID_EVENT_ERROR.getMarker(),
				"""
						Invalid event! {} missing for {} min gen:{}
						most recent event by missing self parent creator:{}
						most recent event by missing self parent creator:{}""",
				event::missingParentsString,
				() -> EventStrings.toMediumString(e),
				() -> currentGenerations.getMinGenerationNonAncient(),
				() -> EventStrings.toShortString(mostRecentEvent.apply(e.getHashedData().getCreatorId())),
				() -> EventStrings.toShortString(mostRecentEvent.apply(e.getUnhashedData().getOtherId()))
		);
	}
}

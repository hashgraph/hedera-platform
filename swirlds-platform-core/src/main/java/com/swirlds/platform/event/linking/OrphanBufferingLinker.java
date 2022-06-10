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

import com.swirlds.common.crypto.Hash;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.purgable.PurgableMap;
import com.swirlds.platform.chatter.protocol.purgable.twomaps.PurgableDoubleMap;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.sync.SyncGenerations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

/**
 * An event linker which buffers out-of-order events until their parents are provided, or they become ancient. An event
 * received out of order is called an orphan. Once an event is no longer an orphan, it is returned by this linker.
 */
public class OrphanBufferingLinker implements EventLinker {
	private static final Logger LOG = LogManager.getLogger();
	private final ParentFinder parentFinder;
	private final Queue<EventImpl> eventOutput;
	private final Queue<EventImpl> newlyLinkedEvents;
	private final PurgableMap<ParentDescriptor, Set<ChildEvent>> missingParents;
	private final PurgableMap<ChatterEventDescriptor, ChildEvent> orphanMap;
	/** the current consensus generations */
	private GraphGenerations currentGenerations = SyncGenerations.GENESIS_GENERATIONS;

	public OrphanBufferingLinker(final ParentFinder parentFinder) {
		this.parentFinder = parentFinder;
		this.eventOutput = new ArrayDeque<>();
		this.newlyLinkedEvents = new ArrayDeque<>();
		this.orphanMap = new PurgableDoubleMap<>(ChatterEventDescriptor::getGeneration);
		this.missingParents = new PurgableDoubleMap<>(ParentDescriptor::generation);
	}

	private static void parentNoLongerMissing(final ChildEvent child, final Hash parentHash, final EventImpl parent) {
		try {
			child.parentNoLongerMissing(parentHash, parent);
		} catch (final IllegalArgumentException e) {
			LOG.error(LogMarker.EXCEPTION.getMarker(),
					"Error while reuniting a child with its parent :( child: {} parent hash: {}",
					child, parentHash, e);
		}
	}

	private static void orphanPurged(final ChatterEventDescriptor key, final ChildEvent orphan) {
		// this should never happen. an events parents should become ancient and at that point it will no longer be an
		// orphan
		if (orphan == null) {
			LOG.error(LogMarker.EXCEPTION.getMarker(), "Null orphan, descriptor: {}", key);
			return;
		}
		orphan.orphanForever();
		LOG.error(LogMarker.EXCEPTION.getMarker(), "Purging an orphan: {}", orphan.getChild());
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

		if (!childEvent.isOrphan()) {
			eventLinked(childEvent.getChild());
			processNewlyLinkedEvents();
			return;
		}

		if (orphanMap.put(event.getDescriptor(), childEvent) != null) {
			// this should never happen
			LOG.error(LogMarker.INVALID_EVENT_ERROR.getMarker(),
					"duplicate orphan: {}", event);
		}

		if (childEvent.isMissingSelfParent()) {
			missingParents
					.computeIfAbsent(childEvent.buildSelfParentDescriptor(), d -> new HashSet<>())
					.add(childEvent);
		}
		if (childEvent.isMissingOtherParent()) {
			missingParents
					.computeIfAbsent(childEvent.buildOtherParentDescriptor(), d -> new HashSet<>())
					.add(childEvent);
		}
	}

	private void processNewlyLinkedEvents() {
		while (!newlyLinkedEvents.isEmpty()) {
			final EventImpl newlyLinked = newlyLinkedEvents.poll();
			final Set<ChildEvent> orphans = missingParents.remove(
					new ParentDescriptor(newlyLinked.getGeneration(), newlyLinked.getBaseHash())
			);
			if (orphans == null || orphans.isEmpty()) {
				continue;
			}
			for (final Iterator<ChildEvent> orphanIterator = orphans.iterator(); orphanIterator.hasNext(); ) {
				final ChildEvent child = orphanIterator.next();
				parentNoLongerMissing(child, newlyLinked.getBaseHash(), newlyLinked);
				if (!child.isOrphan()) {
					eventNoLongerOrphan(child);
					orphanIterator.remove();
				}
			}
		}
	}

	private void eventLinked(final EventImpl event) {
		eventOutput.add(event);
		newlyLinkedEvents.add(event);
	}

	private void eventNoLongerOrphan(final ChildEvent event) {
		eventLinked(event.getChild());
		orphanMap.remove(event.getChild().getBaseEvent().getDescriptor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateGenerations(final GraphGenerations generations) {
		currentGenerations = generations;
		missingParents.purge(
				generations.getMinGenerationNonAncient(),
				this::parentPurged
		);
		// if an orphan becomes ancient, we don't it anymore
		orphanMap.purge(generations.getMinGenerationNonAncient(), OrphanBufferingLinker::orphanPurged);
		processNewlyLinkedEvents();
	}

	private void parentPurged(final ParentDescriptor purgedParent, final Set<ChildEvent> orphans) {
		if (orphans == null) {
			return;
		}
		for (final ChildEvent child : orphans) {
			parentNoLongerMissing(child, purgedParent.hash(), null);
			if (!child.isOrphan()) {
				eventNoLongerOrphan(child);
			}
		}
	}

	/**
	 * Is the event described an orphan we are keeping in the buffer
	 *
	 * @param descriptor
	 * 		the event descriptor
	 * @return true if the event is an orphan this linker is buffering
	 */
	public boolean isOrphan(final ChatterEventDescriptor descriptor) {
		return orphanMap.get(descriptor) != null;
	}

	/**
	 * @return the number of orphans in the buffer
	 */
	public int getNumOrphans() {
		return orphanMap.getSize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasLinkedEvents() {
		return !eventOutput.isEmpty();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventImpl pollLinkedEvent() {
		return eventOutput.poll();
	}

}

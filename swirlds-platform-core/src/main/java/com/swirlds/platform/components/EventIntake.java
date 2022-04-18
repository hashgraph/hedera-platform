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

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphInsertionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INTAKE_EVENT;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;

/**
 * This class is responsible for adding events to {@link Consensus} and EventFlow, as well as
 * notifying event observers.
 */
public class EventIntake {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * The ID of this node
	 */
	private final NodeId selfId;

	/**
	 * A functor that provides access to a {@code Consensus} instance.
	 */
	private final Supplier<Consensus> consensusSupplier;

	/**
	 * A reference to the initial address book for this node.
	 */
	private final AddressBook addressBook;

	/**
	 * An {@link EventObserverDispatcher} instance
	 */
	private final EventObserverDispatcher dispatcher;

	/**
	 * A {@link ShadowGraph} instance, updated whenever the hashgraph is.
	 * Used for gossiping.
	 */
	private final ShadowGraph shadowGraph;


	/**
	 * Constructor
	 *
	 * @param selfId
	 * 		the ID of this node
	 * @param consensusSupplier
	 * 		a functor which provides access to the {@code Consensus} interface
	 * @param addressBook
	 * 		the current address book
	 * @param dispatcher
	 * 		an event observer dispatcher
	 */
	public EventIntake(
			final NodeId selfId,
			final Supplier<Consensus> consensusSupplier,
			final AddressBook addressBook,
			final EventObserverDispatcher dispatcher,
			final ShadowGraph shadowGraph) {
		this.selfId = selfId;
		this.consensusSupplier = consensusSupplier;
		this.addressBook = addressBook;
		this.dispatcher = dispatcher;
		this.shadowGraph = shadowGraph;
	}

	/**
	 * Add an event to the hashgraph
	 *
	 * @param event
	 * 		an event to be added
	 */
	public void addEvent(final EventImpl event) {
		log.debug(SYNC.getMarker(), "{} sees {}", selfId, event);

		dispatcher.preConsensusEvent(event);

		log.debug(INTAKE_EVENT.getMarker(), "Adding {} ", event::toShortString);

		if (smallerThanMinRound(event)) {
			return;
		}

		// record the event in the hashgraph, which results in the events in consEvent reaching consensus
		List<EventImpl> consEvents = consensus().addEvent(event, addressBook);

		final boolean newConsensus = consEvents != null;
		addShadowEvent(event, newConsensus);

		dispatcher.eventAdded(event);

		handleConsensus(consEvents);

		handleStale();
	}

	/**
	 * Add an event to the shadow graph. If events reached consensus ({@code newConsensus} is true), then
	 * expire any sufficiently old event from the shadow graph
	 *
	 * @param event
	 * 		the event to reference with a new shadow event
	 * @param newConsensus
	 * 		true iff inserting {@code event} in the consensus instance caused events to reach consensus
	 */
	private void addShadowEvent(final EventImpl event, final boolean newConsensus) {
		try {
			shadowGraph.addEvent(event);
		} catch (ShadowGraphInsertionException e) {
			log.error(EXCEPTION.getMarker(), "EventIntake: failed to add event {} to shadow graph",
					EventStrings.toMediumString(event), e);
		}

		// If no new consensus, the expired generation has not been increased, so nothing
		// to expire from the shadow graph.
		if (newConsensus) {
			shadowGraph.expireBelow(consensus().getMinRoundGeneration());
		}

	}

	/**
	 * Notify observer of stale events, of all event in the consensus stale event queue.
	 */
	private void handleStale() {
		while (!consensus().getStaleEventQueue().isEmpty()) {
			EventImpl e = consensus().getStaleEventQueue().poll();
			if (e != null) {
				dispatcher.staleEvent(e);

				log.warn(STALE_EVENTS.getMarker(), "Stale event {}", e::toShortString);
			}

		}
	}

	/**
	 * Notify observers that an event has reach consensus. Called on a list of
	 * events returned from {@code Consensus.addEvent}.
	 *
	 * @param consEvents
	 * 		the (new) consensus events to be observed
	 */
	private void handleConsensus(final List<EventImpl> consEvents) {
		if (consEvents != null) {
			for (EventImpl e : consEvents) {
				dispatcher.consensusEvent(e);

//				The local events feature is not currently in use. It will also have to be refactored when we do start
//				using it, so for now it is commented out.
//
//				if (e.isLastInRoundReceived() && Settings.state.saveLocalEvents) {
//					platform.getSignedStateManager().consensusReachedOnRound(
//							e.getRoundReceived(),
//							e.getLastTransTime(),
//							getConsensus()::getAllEvents);
//				}
			}
		}
	}

	/**
	 * Checks if the event will have a round created smaller than minRound. If it does, we should discard it. This
	 * should not cause any issues since that event will be stale anyway.
	 *
	 * @param event
	 * 		the Event to check
	 * @return true if its round will be smaller, false otherwise
	 */
	private boolean smallerThanMinRound(final EventImpl event) {
		// if max roundCreated of the event's parents is smaller than min round, we discard this event.
		//
		// this exception is acceptable when reconnect happens;
		// for example, suppose node3 has been disconnected for a while,
		// after node3 reconnect, when node0 syncs with node3,
		// node0 creates an event whose otherParent is the lastInfo received from node3, i.e., lastInfoByMember.get(3)
		if (event.getMaxRoundCreated() < consensus().getMinRound() && !event.isFromSignedState()) {
			final Consensus consensus = consensus();
			log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
					"""
							Round created {} will be smaller than min round {}, discarding event: {}
							Self parent:{} Other parent:{}
							Consensus min round:{}, max round:{}, last round decided:{}
							Consensus min gen:{}, min gen non ancient:{}, max gen:{}""",
					event::getMaxRoundCreated, consensus::getMinRound, event::toMediumString,
					() -> EventStrings.toShortString(event.getSelfParent()),
					() -> EventStrings.toShortString(event.getOtherParent()),
					consensus::getMinRound, consensus::getMaxRound, consensus::getFameDecidedBelow,
					consensus::getMinRoundGeneration,
					consensus::getMinGenerationNonAncient,
					consensus::getMaxRoundGeneration
			);
			event.setStale(true);
			event.clear();
			return true;
		}
		return false;
	}

	/**
	 * Get a reference to the consensus instance to use
	 *
	 * @return a reference to the consensus instance
	 */
	private Consensus consensus() {
		return consensusSupplier.get();
	}

}

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.components;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.observers.EventObserverDispatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.INTAKE_EVENT;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;
import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;

/**
 * This class is responsible for adding events to {@link Consensus} and notifying event observers, including
 * {@link ConsensusRoundHandler} and
 * {@link com.swirlds.platform.eventhandling.PreConsensusEventHandler}.
 */
public class EventIntake {
	private static final Logger log = LogManager.getLogger();
	/** The ID of this node */
	private final NodeId selfId;
	private final EventLinker eventLinker;
	/** A functor that provides access to a {@code Consensus} instance. */
	private final Supplier<Consensus> consensusSupplier;
	private final ConsensusWrapper consensusWrapper;
	/** A reference to the initial address book for this node. */
	private final AddressBook addressBook;
	/** An {@link EventObserverDispatcher} instance */
	private final EventObserverDispatcher dispatcher;
	/** Collects statistics */
	private final IntakeCycleStats stats;

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
			final EventLinker eventLinker,
			final Supplier<Consensus> consensusSupplier,
			final AddressBook addressBook,
			final EventObserverDispatcher dispatcher,
			final IntakeCycleStats stats) {
		this.selfId = selfId;
		this.eventLinker = eventLinker;
		this.consensusSupplier = consensusSupplier;
		this.consensusWrapper = new ConsensusWrapper(consensusSupplier);
		this.addressBook = addressBook;
		this.dispatcher = dispatcher;
		this.stats = stats;
	}

	/**
	 * Adds an event received from gossip that has been validated without its parents. It must be linked to its parents
	 * before being added to consensus. The linking is done by the {@link EventLinker} provided.
	 *
	 * @param event
	 * 		the event
	 */
	public void addUnlinkedEvent(final GossipEvent event) {
		stats.receivedUnlinkedEvent();
		dispatcher.receivedEvent(event);
		stats.dispatchedReceived();
		eventLinker.linkEvent(event);
		stats.doneLinking();
		while (eventLinker.hasLinkedEvents()) {
			addEvent(eventLinker.pollLinkedEvent());
		}
	}

	/**
	 * Add an event to the hashgraph
	 *
	 * @param event
	 * 		an event to be added
	 */
	public void addEvent(final EventImpl event) {
		stats.startIntake();
		if (!StaticValidators.isValidTimeCreated(event)) {
			event.clear();
			return;
		}
		if (smallerThanMinRound(event)) {
			return;
		}
		stats.doneValidation();
		log.debug(SYNC.getMarker(), "{} sees {}", selfId, event);
		dispatcher.preConsensusEvent(event);
		log.debug(INTAKE_EVENT.getMarker(), "Adding {} ", event::toShortString);
		stats.dispatchedPreConsensus();
		// record the event in the hashgraph, which results in the events in consEvent reaching consensus
		final List<ConsensusRound> consRounds = consensusWrapper.addEvent(event, addressBook);
		stats.addedToConsensus();
		dispatcher.eventAdded(event);
		stats.dispatchedAdded();
		if (consRounds != null) {
			consRounds.forEach(this::handleConsensus);
		}
		stats.dispatchedRound();
		handleStale();
		stats.dispatchedStale();
	}

	/**
	 * Notify observer of stale events, of all event in the consensus stale event queue.
	 */
	private void handleStale() {
		while (!consensus().getStaleEventQueue().isEmpty()) {
			final EventImpl e = consensus().getStaleEventQueue().poll();
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
	 * @param consensusRound
	 * 		the (new) consensus round to be observed
	 */
	private void handleConsensus(final ConsensusRound consensusRound) {
		if (consensusRound != null) {
			eventLinker.updateGenerations(consensusRound.getGenerations());
			dispatcher.consensusRound(consensusRound);
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

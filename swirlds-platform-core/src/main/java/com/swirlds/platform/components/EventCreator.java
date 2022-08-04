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

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.SelfEventStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

/**
 * This class encapsulates the workflow required to create new events.
 */
public class EventCreator {
	private static final Logger log = LogManager.getLogger();

	/**
	 * An implementor of {@link SwirldMainManager}
	 */
	private final SwirldMainManager swirldMainManager;

	/**
	 * This node's address book ID
	 */
	private final NodeId selfId;

	/**
	 * An implementor of {@link Signer}
	 */
	private final Signer signer;

	/**
	 * Supplies the key generation number from the hashgraph
	 */
	private final Supplier<GraphGenerations> graphGenerationsSupplier;

	/**
	 * An implementor of {@link TransactionSupplier}
	 */
	private final TransactionSupplier transactionSupplier;

	/**
	 * An implementor of {@link EventHandler}
	 */
	private final EventHandler newEventHandler;

	/**
	 * This hashgraph's {@link EventMapper}
	 */
	private final EventMapper eventMapper;

	/** Stores the most recent event created by me */
	private final SelfEventStorage selfEventStorage;

	/**
	 * This hashgraph's {@link TransactionTracker}
	 */
	private final TransactionTracker transactionTracker;

	/**
	 * An implementor of {@link TransactionPool}
	 */
	private final TransactionPool transactionPool;

	/** This object is used for checking whether this node should create an event or not */
	private final EventCreationRules eventCreationRules;

	/**
	 * Construct a new EventCreator.
	 *
	 * @param swirldMainManager
	 * 		responsible for interacting with SwirldMain
	 * @param selfId
	 * 		the ID of this node
	 * @param signer
	 * 		responsible for signing new events
	 * @param graphGenerationsSupplier
	 * 		supplies the key generation number from the hashgraph
	 * @param transactionSupplier
	 * 		this method supplies transactions that should be inserted into newly created events
	 * @param newEventHandler
	 * 		this method is passed all newly created events
	 * @param selfEventStorage
	 * 		stores the most recent event created by me
	 * @param eventMapper
	 * 		the object that tracks the most recent events from each node
	 * @param transactionTracker
	 * 		the object that tracks user transactions in the hashgraph
	 * @param transactionPool
	 * 		the TransactionPool
	 * @param eventCreationRules
	 * 		the object used for checking whether should create an event or not
	 */
	public EventCreator(
			final SwirldMainManager swirldMainManager,
			final NodeId selfId,
			final Signer signer,
			final Supplier<GraphGenerations> graphGenerationsSupplier,
			final TransactionSupplier transactionSupplier,
			final EventHandler newEventHandler,
			final EventMapper eventMapper,
			final SelfEventStorage selfEventStorage,
			final TransactionTracker transactionTracker,
			final TransactionPool transactionPool,
			final EventCreationRules eventCreationRules) {
		this.swirldMainManager = swirldMainManager;
		this.selfId = selfId;
		this.signer = signer;
		this.graphGenerationsSupplier = graphGenerationsSupplier;
		this.transactionSupplier = transactionSupplier;
		this.newEventHandler = newEventHandler;
		this.eventMapper = eventMapper;
		this.selfEventStorage = selfEventStorage;
		this.transactionTracker = transactionTracker;
		this.transactionPool = transactionPool;
		this.eventCreationRules = eventCreationRules;
	}

	public void createGenesisEvent() {
		handleNewEvent(buildEvent(null, null));
	}

	/**
	 * Create a new event and push it into the gossip/consensus pipeline.
	 *
	 * @param otherId
	 * 		the node ID that will supply the other parent for this event
	 */
	public boolean createEvent(final long otherId) {
		if (eventCreationRules.shouldCreateEvent() == EventCreationRuleResponse.DONT_CREATE) {
			return false;
		}

		// Give the app one last chance to create a non-system transaction and give the platform
		// one last chance to create a system transaction.
		swirldMainManager.preEvent();

		// We don't want to create multiple events with the same other parent, so we have to check if we
		// already created an event with this particular other parent. We still want to create an event if there
		// are freeze transactions
		if (hasOtherParentAlreadyBeenUsed(otherId) && hasNoFreezeTransactions()) {
			return false;
		}

		final EventImpl otherParent = eventMapper.getMostRecentEvent(otherId);
		final EventImpl selfParent = selfEventStorage.getMostRecentSelfEvent();

		if (eventCreationRules.shouldCreateEvent(selfParent, otherParent) == EventCreationRuleResponse.DONT_CREATE) {
			return false;
		}

		// Don't create an event if both parents are old.
		if (areBothParentsAncient(selfParent, otherParent)) {
			log.debug(CREATE_EVENT.getMarker(),
					"Both parents are ancient, selfParent: {}, otherParent: {}",
					() -> EventUtils.toShortString(selfParent), () -> EventUtils.toShortString(otherParent));
			return false;
		}

		handleNewEvent(buildEvent(selfParent, otherParent));
		return true;
	}

	private void handleNewEvent(final EventImpl event) {
		logEventCreation(event);
		selfEventStorage.setMostRecentSelfEvent(event);
		newEventHandler.handleEvent(event);
	}

	/**
	 * Construct an event object.
	 */
	protected EventImpl buildEvent(
			final EventImpl selfParent,
			final EventImpl otherParent) {

		final BaseEventHashedData hashedData = new BaseEventHashedData(
				selfId.getId(),
				getEventGeneration(selfParent),
				getEventGeneration(otherParent),
				getEventHash(selfParent),
				getEventHash(otherParent),
				getTimeCreated(Instant.now(), selfParent),
				transactionSupplier.getTransactions());
		CryptoFactory.getInstance().digestSync(hashedData);

		final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
				getOtherParentCreatorId(otherParent),
				signer.sign(hashedData.getHash().getValue()));

		return new EventImpl(hashedData, unhashedData, selfParent, otherParent);
	}

	/**
	 * Check if the most recent event from the given node has been used as an other parent by an
	 * event created by the current node.
	 *
	 * @param otherId
	 * 		the ID of the node supplying the other parent
	 */
	protected boolean hasOtherParentAlreadyBeenUsed(final long otherId) {
		return !selfId.equalsMain(otherId) && eventMapper.hasMostRecentEventBeenUsedAsOtherParent(otherId);
	}

	/**
	 * Check if there are freeze transactions waiting to be inserted into an event.
	 */
	protected boolean hasNoFreezeTransactions() {
		return !(transactionPool.numUserTransForEvent() > 0 && transactionTracker.getNumUserTransEvents() == 0)
				&& transactionPool.numFreezeTransEvent() == 0;
	}

	/**
	 * Get the creator ID of the event. If null return self ID.
	 *
	 * @param otherParent
	 * 		an other-parent event
	 * @return the creator ID as {@code long} of the given event, or the self-ID
	 * 		if the given event is {@code null}
	 */
	protected long getOtherParentCreatorId(final EventImpl otherParent) {
		if (otherParent == null) {
			return EventConstants.CREATOR_ID_UNDEFINED;
		} else {
			return otherParent.getCreatorId();
		}
	}

	/**
	 * Check if both parents are ancient
	 *
	 * @param selfParent
	 * 		the self-parent event
	 * @param otherParent
	 * 		the other-parent event
	 * @return true iff both parents are ancient
	 */
	protected boolean areBothParentsAncient(
			final EventImpl selfParent,
			final EventImpl otherParent) {
		// This is introduced as a fix for problems seen while recovering from the mainnet hardware
		// crash on 3 June 2019. Then, 3 out of 10 nodes went offline (due to hardware problems) and had only
		// old events in the state. When the nodes started back up,
		// nodes that previously crashed synced with other nodes that crashed. This created events where both
		// parents are old, and these events could not be entered into the hashgraph on the nodes that created
		// them, and they were not gossipped out.
		// Update 15 November 2021: The code has since changed and creating ancient events no longer breaks the system.
		// But it does not make sense create an ancient event, so this rule is still in place.
		final GraphGenerations generations = graphGenerationsSupplier.get();
		if (!generations.areAnyEventsAncient()) {
			// if there are no ancient event yet, we return immediately. This is to account for genesis, where both
			// parents will be null
			return false;
		}
		// if a parent is null, it's the same as if it were ancient
		return (selfParent == null || generations.isAncient(selfParent))
				&& (otherParent == null || generations.isAncient(otherParent));
	}

	/**
	 * Compute the creation time of a new event.
	 *
	 * @param now
	 * 		a time {@code Instant}
	 * @param selfParent
	 * 		the self-parent of the event to be created
	 * @return a time {@code Instant} which defines the creation time of an event
	 */
	protected Instant getTimeCreated(final Instant now, final EventImpl selfParent) {
		Instant timeCreated = now;

		if (selfParent != null) {
			// Ensure that events created by self have a monotonically increasing creation time.
			// This is true when the computer's clock is running normally.
			// If the computer's clock is reset to an earlier time, then the Instant.now() call
			// above may be earlier than the self-parent's creation time. In that case, it
			// advances to several nanoseconds later than the parent. If the clock is only set back
			// a short amount, then the timestamps will only slow down their advancement for a
			// little while, then go back to advancing one second per second. If the clock is set
			// far in the future, then the parent is created, then the clock is set to the correct
			// time, then the advance will slow down for a long time. One solution for that is to
			// generate no events for enough rounds that the parent will not exist (will be null
			// at this point), and then the correct clock time can be used again. (Assuming this
			// code has implemented nulling out parents from extremely old rounds).
			// If event x is followed by y, then y should be at least n nanoseconds later than x,
			// where n is the number of transactions in x (so each can have a different time),
			// or n=1 if there are no transactions (so each event is a different time).

			long minimumTimeIncrement = 1;
			if (selfParent.getTransactions() != null && selfParent.getTransactions().length > 0) {
				minimumTimeIncrement = selfParent.getTransactions().length;
			}

			final Instant minimumNextEventTime = selfParent.getTimeCreated().plusNanos(minimumTimeIncrement);

			if (timeCreated.isBefore(minimumNextEventTime)) {
				timeCreated = minimumNextEventTime;
			}
		}

		return timeCreated;
	}

	/**
	 * Get the generation of an event. Returns {@value EventConstants#GENERATION_UNDEFINED} for null events.
	 *
	 * @param event
	 * 		an event
	 * @return the generation number of the given event,
	 * 		or {@value EventConstants#GENERATION_UNDEFINED} is the event is {@code null}
	 */
	protected long getEventGeneration(final EventImpl event) {
		if (event == null) {
			return EventConstants.GENERATION_UNDEFINED;
		}
		return event.getGeneration();
	}

	/**
	 * Get the base hash of an event. Returns null for null events.
	 *
	 * @param event
	 * 		an event
	 * @return a {@code byte[]} which contains the hash bytes of the given event, or {@code null}
	 * 		if the given event is {@code null}
	 */
	protected byte[] getEventHash(final EventImpl event) {
		if (event == null) {
			return null;
		}
		return event.getBaseHash().getValue();
	}

	/**
	 * Write to the log (if configured) every time an event is created.
	 *
	 * @param event
	 * 		the created event to be logged
	 */
	protected void logEventCreation(final EventImpl event) {
		log.debug(CREATE_EVENT.getMarker(), "Creating {}", event::toMediumString);
	}
}

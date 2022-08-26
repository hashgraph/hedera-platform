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

package com.swirlds.platform.event.creation;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.SwirldMainManager;
import com.swirlds.platform.components.TransactionSupplier;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

/**
 * This class encapsulates the workflow required to create new events.
 */
public class ChatterEventCreator {
	private static final Logger LOG = LogManager.getLogger();

	/** This node's address book ID */
	private final NodeId selfId;
	/** An implementor of {@link SwirldMainManager} */
	private final SwirldMainManager swirldMainManager;
	/** An implementor of {@link Signer} */
	private final Signer signer;
	/** An implementor of {@link TransactionSupplier} */
	private final TransactionSupplier transactionSupplier;
	/** Consumes the events that are created */
	private final Consumer<GossipEvent> newEventHandler;
	/** This hashgraph's {@link EventMapper} */
	private final LongFunction<GossipEvent> mostRecentEventById;
	/** This object is used for checking whether this node should create an event or not */
	private final EventCreationRules eventCreationRules;
	/** Used for hashing the event when created */
	private final Cryptography hasher;

	public ChatterEventCreator(
			final SwirldMainManager swirldMainManager,
			final NodeId selfId,
			final Signer signer,
			final TransactionSupplier transactionSupplier,
			final Consumer<GossipEvent> newEventHandler,
			final LongFunction<GossipEvent> mostRecentEventById,
			final EventCreationRules eventCreationRules) {
		this.swirldMainManager = swirldMainManager;
		this.selfId = selfId;
		this.signer = signer;
		this.transactionSupplier = transactionSupplier;
		this.newEventHandler = newEventHandler;
		this.mostRecentEventById = mostRecentEventById;
		this.eventCreationRules = eventCreationRules;
		this.hasher = CryptoFactory.getInstance();
	}

	/**
	 * Create a genesis event with no parents
	 */
	public void createGenesisEvent() {
		handleNewEvent(buildEvent(null, null));
	}

	/**
	 * Create a new event and push it into the gossip/consensus pipeline.
	 *
	 * @param otherId
	 * 		the node ID that will supply the other parent for this event
	 * @return true if the event was created, false if not
	 */
	public boolean createEvent(final long otherId) {
		final EventCreationRuleResponse basicRulesResponse = eventCreationRules.shouldCreateEvent();
		if (basicRulesResponse == EventCreationRuleResponse.DONT_CREATE) {
			return false;
		}
		final GossipEvent selfParent = mostRecentEventById.apply(selfId.getId());
		final GossipEvent otherParent = mostRecentEventById.apply(otherId);
		// if the basic rules returned a CREATE, this overrides all subsequent rules, so we don't check the parent based
		// rules
		if (basicRulesResponse != EventCreationRuleResponse.CREATE &&
				eventCreationRules.shouldCreateEvent(selfParent, otherParent) == EventCreationRuleResponse.DONT_CREATE) {
			return false;
		}

		// Give the app one last chance to create a non-system transaction and give the platform
		// one last chance to create a system transaction.
		swirldMainManager.preEvent();

		handleNewEvent(buildEvent(selfParent, otherParent));
		return true;
	}

	private void handleNewEvent(final GossipEvent event) {
		logEventCreation(event);
		newEventHandler.accept(event);
	}

	/**
	 * Construct an event object.
	 */
	private GossipEvent buildEvent(
			final BaseEvent selfParent,
			final BaseEvent otherParent) {

		final BaseEventHashedData hashedData = new BaseEventHashedData(
				selfId.getId(),
				EventUtils.getEventGeneration(selfParent),
				EventUtils.getEventGeneration(otherParent),
				EventUtils.getEventHash(selfParent),
				EventUtils.getEventHash(otherParent),
				EventUtils.getChildTimeCreated(Instant.now(), selfParent),
				transactionSupplier.getTransactions());
		hasher.digestSync(hashedData);

		final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
				EventUtils.getCreatorId(otherParent),
				signer.sign(hashedData.getHash().getValue()));
		final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);
		gossipEvent.buildDescriptor();
		return gossipEvent;
	}

	/**
	 * Write to the log (if configured) every time an event is created.
	 *
	 * @param event
	 * 		the created event to be logged
	 */
	protected void logEventCreation(final GossipEvent event) {
		LOG.debug(CREATE_EVENT.getMarker(), "Creating {}", event::toString);
	}
}

/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.platform;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.platform.stats.HashgraphStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.BETA_MIRROR_NODE;
import static com.swirlds.logging.LogMarker.EVENT_SIG;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

public class EventValidator {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/**
	 * A predicate to determine whether a task's event data is a duplicate of an event's
	 * data that is already in the hashgraph
	 */
	private final Predicate<EventImpl> isDuplicateInHashgraph;
	private final Function<Long, EventImpl> mostRecentEvent;

	/**
	 * A predicate to determine whether a node is a zero-stake node
	 */
	private final Predicate<Long> isZeroStakeNode;

	/**
	 * A predicate to determine whether an event's data has a cryptographically valid signature
	 */
	private final Predicate<EventImpl> hasValidSignature;

	/**
	 * The ID of this node. Used for logging.
	 */
	private final NodeId selfId;

	/**
	 * A functor to use for adding a new event to the hashgraph
	 */
	private final Consumer<EventImpl> eventIntake;

	/**
	 * A statistics accumulator. Used for reporting certain validation states,
	 */
	private final HashgraphStats stats;

	/**
	 * A functor that provides access to a {@link Consensus} instance. Used only for
	 * event construction.
	 */
	private final Supplier<Consensus> consensusSupplier;

	/**
	 * Look up an event by its hash
	 */
	private final Function<Hash, EventImpl> eventByHash;

	/**
	 * Test constructor
	 *
	 * @param selfId
	 * 		the node ID
	 * @param isDuplicateInHashgraph
	 * 		predicate that determines whether a given Event is a duplicate of an event already in the hashgraph
	 * @param isZeroStateNode
	 * 		predicate that defines whether a node with a given node ID is a zero-stake node
	 * @param hasValidSignature
	 * 		predicate that defines whether an event has a valid signature
	 */
	EventValidator(
			final long selfId,
			final Predicate<EventImpl> isDuplicateInHashgraph,
			final Predicate<Long> isZeroStateNode,
			final Predicate<EventImpl> hasValidSignature,
			final Supplier<Consensus> consensusSupplier,
			final HashgraphStats stats) {
		this.selfId = new NodeId(false, selfId);
		this.isDuplicateInHashgraph = isDuplicateInHashgraph;
		this.mostRecentEvent = (l) -> null;
		this.isZeroStakeNode = isZeroStateNode;
		this.hasValidSignature = hasValidSignature;
		this.eventIntake = null;
		this.stats = stats;
		this.consensusSupplier = consensusSupplier;
		this.eventByHash = null;
	}

	/**
	 * Production constructor
	 *
	 * @param selfId
	 * 		the node ID
	 * @param isDuplicateInHashgraph
	 * 		predicate that determines whether a given Event is a duplicate of an event already in the hashgraph
	 * @param mostRecentEvent
	 * 		returns the most recent event received from a particular creator
	 * @param isZeroStateNode
	 * 		predicate that defines whether a node with a given node ID is a zero-stake node
	 * @param publicKey
	 * 		a functor that returns the public key of a given {@code EventImpl}
	 */
	EventValidator(
			final NodeId selfId,
			final Predicate<EventImpl> isDuplicateInHashgraph,
			final Function<Long, EventImpl> mostRecentEvent,
			final Predicate<Long> isZeroStateNode,
			final Function<EventImpl, PublicKey> publicKey,
			final Consumer<EventImpl> eventIntake,
			final HashgraphStats stats,
			final Supplier<Consensus> consensusSupplier,
			final Function<Hash, EventImpl> eventByHash) {
		this.selfId = selfId;
		this.isDuplicateInHashgraph = isDuplicateInHashgraph;
		this.mostRecentEvent = mostRecentEvent;
		this.isZeroStakeNode = isZeroStateNode;
		this.hasValidSignature = (EventImpl event) -> hasValidSignatureImpl(event, publicKey.apply(event));
		this.eventIntake = eventIntake;
		this.stats = stats;
		this.consensusSupplier = consensusSupplier;
		this.eventByHash = eventByHash;
	}

	/**
	 * Implementor for the {@code hashValidSignature} field
	 *
	 * @param event
	 * 		the event to be examined
	 * @param publicKey
	 * 		the public used to validate the event signature
	 * @return true iff the signature is crypto-verified to be correct
	 */
	private static boolean hasValidSignatureImpl(final EventImpl event, final PublicKey publicKey) {
		if (!Settings.verifyEventSigs) {
			// if we aren't verifying signatures, then say they're all valid
			return true;
		} else {
			// we are verifying signatures, but this event hasn't been verified yet
			log.debug(EVENT_SIG.getMarker(),
					"event signature is about to be verified. {}",
					event::toShortString);

			final boolean valid = Crypto.verifySignature(
					event.getBaseHash().getValue(),
					event.getSignature(),
					publicKey);

			if (!valid) {
				final byte[] signatureCopy = event.getSignature();
				log.error(INVALID_EVENT_ERROR.getMarker(),
						"failed the signature check {} with sig \n     {} and hash \n     {}",
						() -> event,
						() -> CommonUtils.hex(signatureCopy),
						event::getBaseHash);
			}

			return valid;
		}
	}

	/**
	 * Determine whether total size of all transactions in a given task is too
	 * large to be accepted.
	 *
	 * @param event
	 * 		the event to be validated
	 * @return true iff the total size of all transactions in the task not too large to be accepted.
	 */
	private static boolean isValidTransactionsSize(final EventImpl event) {
		if (event.getTransactions() == null) {
			return true;
		}

		// Sum the total size of all transactions about to be included in this event
		int tmpEventTransSize = 0;
		for (Transaction t : event.getTransactions()) {
			tmpEventTransSize += t.getSerializedLength();
		}
		final int finalEventTransSize = tmpEventTransSize;

		// Ignore & log if we have encountered a transaction larger than the limit
		// This might be due to a malicious node in the network
		if (tmpEventTransSize > Settings.maxTransactionBytesPerEvent) {
			log.error(TESTING_EXCEPTIONS.getMarker(),
					"maxTransactionBytesPerEvent exceeded by event {} with a total size of {} bytes",
					event::toShortString,
					() -> finalEventTransSize);
			return false;
		}

		return true;
	}

	/**
	 * The method defines task validity for gossiped event data, and processes event information
	 * received from other nodes. It does the following, in this order:
	 * <pre>
	 * (a) instantiates an event object from parents in the hashgraph
	 * (b) assesses the validity of the event,
	 * (d) invokes the given stats update callbacks as appropriate, and
	 * (e) adds the event to the hashgraph intake queue, if it is a valid event.
	 * </pre>
	 *
	 * @param hashedData
	 * 		the hashed data for the event
	 * @param unhashedData
	 * 		the unhashed data for the event
	 */
	public void validateEvent(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
		try {
			final EventImpl event = buildEvent(hashedData, unhashedData);
			final EventStatus eventStatus = getStatus(event);

			updateStats(event, eventStatus);

			// If this event is not valid, then silently ignore it. It will soon be garbage collected.
			if (eventStatus != EventStatus.VALID) {
				// Decrement the event counter if the event is invalid. An optimization is to validate
				// the data components of an event before construction. This can be done later.
				event.clear();
				return;
			}

			eventIntake.accept(event);

		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "Error while processing intake event", e);
		}
	}

	/**
	 * Evaluate a {@code ValidateEventTask} instance for validity.
	 *
	 * @param event
	 * 		an event instance to assay for validity
	 * @return A {@code Status} enum instance which defines the assayed status
	 */
	protected EventStatus getStatus(final EventImpl event) {
		if (isFromZeroStakeNode(event)) {
			return EventStatus.INVALID_ZERO_STAKE_NODE;
		}

		if (!isValidTimeCreated(event)) {
			return EventStatus.INVALID_CREATION_TIME;
		}

		if (!isValidTransactionsSize(event)) {
			return EventStatus.INVALID_TRANSACTIONS_SIZE;
		}

		if (isDuplicateInHashgraph.test(event)) {
			return EventStatus.INVALID_DUPLICATE_EVENT;
		}

		if (isMissingOP(event)) {
			return EventStatus.INVALID_MISSING_OTHER_PARENT;
		}

		if (isMissingSP(event)) {
			return EventStatus.INVALID_MISSING_SELF_PARENT;
		}

		// Execute signature validation last
		if (!hasValidSignature.test(event)) {
			return EventStatus.INVALID_EVENT_SIGNATURE;
		}

		return EventStatus.VALID;
	}

	/**
	 * Update the relevant fields in statistics accumulator {@code this.stats} from the
	 * status of the event.
	 *
	 * @param eventStatus
	 * 		the assessed status of the event
	 */
	protected void updateStats(final EventImpl event, final EventStatus eventStatus) {

		stats.receivedEvent(event);

		if (eventStatus == EventStatus.INVALID_DUPLICATE_EVENT) {
			stats.duplicateEvent();
		} else {
			stats.nonDuplicateEvent();
		}

		if (eventStatus == EventStatus.INVALID_EVENT_SIGNATURE) {
			stats.invalidEventSignature();
		}

	}

	/**
	 * Build the event.
	 *
	 * @param hashedData
	 * 		the hashed data for the event
	 * @param unhashedData
	 * 		the unhashed data for the event
	 */
	private EventImpl buildEvent(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {

		final EventImpl event = new EventImpl(
				hashedData,
				unhashedData,
				eventByHash.apply(hashedData.getSelfParentHash()),
				eventByHash.apply(hashedData.getOtherParentHash()));

		CryptoFactory.getInstance().digestSync(event.getBaseEventHashedData());

		return event;
	}

	/**
	 * For an event's parent p, if p is not ancient and not present, reject the event.
	 * if a parent is missing, then its generation should be smaller than the minimum
	 * generation. if its not smaller, then we do not accept this event.
	 *
	 * @param event
	 * 		the event to validate
	 * @return true iff the event's other-parent is null and not ancient
	 */
	private boolean isMissingOP(EventImpl event) {
		final long minGenerationNonAncient = consensusSupplier.get().getMinGenerationNonAncient();

		final boolean absent = event.getOtherParent() == null;

		final boolean missingOP = absent && requiredParent(event.getOtherParentGen(), minGenerationNonAncient);

		if (missingOP) {
			logMissingParent(event, minGenerationNonAncient, false);
		}

		return missingOP;
	}

	/**
	 * For an event's parent p, if p is not ancient and not present, reject the event.
	 * if a parent is missing, then its generation should be smaller than the minimum
	 * generation. if its not smaller, then we do not accept this event.
	 *
	 * @param event
	 * 		the event to validate
	 * @return true iff the event's self-parent is null and not ancient
	 */
	private boolean isMissingSP(EventImpl event) {
		final long minGenerationNonAncient = consensusSupplier.get().getMinGenerationNonAncient();

		final boolean absent = event.getSelfParent() == null;

		final boolean missingSP = absent && requiredParent(event.getSelfParentGen(), minGenerationNonAncient);

		if (missingSP) {
			logMissingParent(event, minGenerationNonAncient, true);
		}

		return missingSP;
	}

	private void logMissingParent(
			final EventImpl event,
			final long minGenerationNonAncient,
			final boolean selfParent) {
		final EventImpl mostRecent = mostRecentEvent.apply(
				selfParent ? event.getCreatorId() : event.getOtherId()
		);
		log.error(INVALID_EVENT_ERROR.getMarker(),
				"{} Invalid event! {} missing {} min gen:{} most recent event by missing parent creator:{}",
				() -> selfId,
				() -> selfParent ? "selfParent" : "otherParent",
				event::toMediumString,
				() -> minGenerationNonAncient,
				() -> EventStrings.toShortString(mostRecent));
	}

	/**
	 * An event's parent is required iff (a) the event does have that parent event, and (b) that
	 * parent event is non-ancient.
	 *
	 * @param parentGeneration
	 * 		the generation of the parent event
	 * @param minGenerationNonAncient
	 * 		see {@link Consensus#getMinGenerationNonAncient()}
	 * @return true iff the event's parent is required
	 */
	private static boolean requiredParent(final long parentGeneration, final long minGenerationNonAncient) {
		final boolean required;

		final boolean hasParent = parentGeneration != EventImpl.NO_EVENT_GEN;

		final boolean currentParent = parentGeneration >= minGenerationNonAncient;

		// Adapting here to the current impl of ConsensusImpl.getMinGenerationNonAncient
		final boolean hasAncientRound = minGenerationNonAncient != RoundInfo.MIN_FAMOUS_WITNESS_GENERATION_UNDEFINED;
		if (hasAncientRound) {
			required = hasParent && currentParent;
		} else {
			required = hasParent;
		}

		return required;
	}


	/**
	 * Determine if the {@code ValidateEventTask} contains data received from zero-stake node. Log a mirror
	 * node error if so.
	 *
	 * @param event
	 * 		the event to validate
	 * @return true iff the data was received from a zero-stake node
	 */
	private boolean isFromZeroStakeNode(final EventImpl event) {
		// If beta mirror node logic is enabled and the event originated from a node known to have a zero stake,
		// then we should discard this event and not even worry about validating the signature in order
		// to prevent a potential DoS or DDoS attack from a zero stake node
		if (isZeroStakeNode.test(event.getCreatorId())) {
			log.debug(BETA_MIRROR_NODE.getMarker(),
					"Event Intake: Received event data from a zero stake node {}}",
					event::toShortString);
			return true;
		}

		return false;
	}

	/**
	 * Determine whether a given event has a valid creation time.
	 *
	 * @param event
	 * 		the event to be validated
	 * @return true iff the creation time of the event is strictly after the
	 * 		creation time of its self-parent
	 */
	private boolean isValidTimeCreated(final EventImpl event) {
		if (event.getSelfParent() != null) {

			EventImpl selfParent = event.getSelfParent();
			if (selfParent != null && !event.getTimeCreated().isAfter(selfParent.getTimeCreated())) {

				log.debug(TESTING_EXCEPTIONS.getMarker(), () -> String.format(
						"%s Event timeCreated ERROR event %s" +
								"created:%s, parent created:%s",
						selfId,
						event.toMediumString(),
						event.getTimeCreated().toString(),
						selfParent.getTimeCreated().toString()));
				return false;
			}
		}

		return true;
	}

	/**
	 * The validity status of an event.
	 */
	public enum EventStatus {
		VALID,
		INVALID_ZERO_STAKE_NODE,
		INVALID_EVENT_SIGNATURE,
		INVALID_CREATION_TIME,
		INVALID_TRANSACTIONS_SIZE,
		INVALID_DUPLICATE_EVENT,
		INVALID_MISSING_SELF_PARENT,
		INVALID_MISSING_OTHER_PARENT,
	}

}

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

package com.swirlds.platform;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializableRunningHashable;
import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.common.events.ConsensusData;
import com.swirlds.common.events.ConsensusEvent;
import com.swirlds.common.events.Event;
import com.swirlds.common.events.EventSerializationOptions;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.InternalEventData;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * An internal platform event. It holds all the event data relevant to the platform. It implements the Event interface
 * which is a public-facing form of an event.
 */
@ConstructableIgnored
public class EventImpl extends AbstractSerializableHashable implements Comparable<EventImpl>, Event,
		OptionalSelfSerializable<EventSerializationOptions>, Timestamped, SerializableRunningHashable {

	/** The base event information, including some gossip specific information */
	private GossipEvent baseEvent;
	/** Consensus data calculated for an event */
	private ConsensusData consensusData;
	/** Internal data used for calculating consensus */
	private InternalEventData internalEventData;

	private RunningHash runningHash;

	/**
	 * Tracks if this event was read out of a signed state.
	 */
	private boolean fromSignedState;

	public EventImpl() {
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData) {
		this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), null, null);
		updateConsensusDataGeneration();
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final ConsensusData consensusData) {
		this(baseEventHashedData, baseEventUnhashedData, consensusData, null, null);
	}

	public EventImpl(
			final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), selfParent, otherParent);
		updateConsensusDataGeneration();
	}

	public EventImpl(
			final GossipEvent gossipEvent,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		this(gossipEvent, new ConsensusData(), selfParent, otherParent);
		updateConsensusDataGeneration();
	}

	/**
	 * This constructor is used in {@link StreamEventParser} when parsing events from stream
	 *
	 * @param consensusEvent
	 */
	EventImpl(final ConsensusEvent consensusEvent) {
		buildFromConsensusEvent(consensusEvent);
	}

	public EventImpl(
			final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final ConsensusData consensusData,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		this(new GossipEvent(baseEventHashedData, baseEventUnhashedData), consensusData, selfParent, otherParent);
	}

	public EventImpl(
			final GossipEvent baseEvent,
			final ConsensusData consensusData,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		CommonUtils.throwArgNull(baseEvent, "baseEvent");
		CommonUtils.throwArgNull(baseEvent.getHashedData(), "baseEventDataHashed");
		CommonUtils.throwArgNull(baseEvent.getUnhashedData(), "baseEventDataNotHashed");
		CommonUtils.throwArgNull(consensusData, "consensusData");

		this.baseEvent = baseEvent;
		this.consensusData = consensusData;
		this.internalEventData = new InternalEventData(selfParent, otherParent);

		EventCounter.eventCreated();

		setDefaultValues();
	}

	/**
	 * initialize RunningHash instance
	 */
	private void setDefaultValues() {
		runningHash = new RunningHash();
		if (baseEvent.getHashedData().getHash() != null) {
			baseEvent.buildDescriptor();
		}
	}

	/**
	 * Set this event's generation to be 1 more than the max of its parents.
	 *
	 * @deprecated
	 */
	@Deprecated(forRemoval = true) // there is no need to store the events generation inside consensusData
	public void updateConsensusDataGeneration() {
		consensusData.setGeneration(baseEvent.getHashedData().getGeneration());
	}

	/**
	 * Calls {@link InternalEventData#clear()}
	 */
	public void clear() {
		internalEventData.clear();
	}

	/**
	 * Set the consensusTimestamp to an estimate of what it will be when consensus is reached even if it has already
	 * reached consensus. Callers are responsible for checking the consensus status of this event and using the
	 * consensus time or estimated time appropriately.
	 *
	 * Estimated consensus times are predicted only here and in Platform.estimateTime().
	 *
	 * @param selfId
	 * 		the ID of this platform
	 * @param avgSelfCreatedTimestamp
	 * 		self event consensus timestamp minus time created
	 * @param avgOtherReceivedTimestamp
	 * 		other event consensus timestamp minus time received
	 */
	public synchronized void estimateTime(final NodeId selfId, final double avgSelfCreatedTimestamp,
			final double avgOtherReceivedTimestamp) {
		/* a base time */
		Instant t;
		/* number of seconds to add to the base time */
		double sec;

		if (selfId.equalsMain(getCreatorId())) {
			// event by self
			t = getTimeCreated();
			// seconds from self creating an event to the consensus timestamp that event receives
			sec = avgSelfCreatedTimestamp; // secSC2T
		} else {
			// event by other
			t = getTimeReceived();
			// seconds from receiving an event (not by self) to the timestamp that event receives
			sec = avgOtherReceivedTimestamp; // secOR2T
		}

		sec = 0; // this will be changed to give a better estimate than 0 or those above

		setEstimatedTime(t.plus((long) (sec * 1_000_000_000.0), ChronoUnit.NANOS));
	}

	/**
	 * Returns the timestamp of the last transaction in this event. If this event has no transaction, then the timestamp
	 * of the event will be returned
	 *
	 * @return timestamp of the last transaction
	 */
	public Instant getLastTransTime() {
		if (getTransactions() == null) {
			return null;
		}
		// this is a special case. if an event has 0 or 1 transactions, the timestamp of the last transaction can be
		// considered to be the same, equivalent to the timestamp of the event
		if (getTransactions().length <= 1) {
			return getConsensusTimestamp();
		}
		return getTransactionTime(getTransactions().length - 1);
	}

	/**
	 * Returns the timestamp of the transaction with given index in this event
	 *
	 * @param transactionIndex
	 * 		index of the transaction in this event
	 * @return timestamp of the given index transaction
	 */
	public Instant getTransactionTime(int transactionIndex) {
		if (getConsensusTimestamp() == null || getTransactions() == null) {
			return null;
		}
		if (transactionIndex >= getTransactions().length) {
			throw new IllegalArgumentException("Event does not have a transaction with index:" + transactionIndex);
		}
		return getConsensusTimestamp().plusNanos(transactionIndex * Settings.minTransTimestampIncrNanos);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final EventImpl event = (EventImpl) o;

		return new EqualsBuilder()
				.append(baseEvent, event.baseEvent)
				.append(consensusData, event.consensusData)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(baseEvent)
				.append(consensusData)
				.toHashCode();
	}

	/**
	 * Events compare by generation. So sorting is always a topological sort. Returns -1 if this.generation
	 * is less than other.generation, 1 if greater, 0 if equal.
	 *
	 * @param other
	 *        {@inheritDoc}
	 * @return {@inheritDoc}
	 */
	@Override
	public synchronized int compareTo(EventImpl other) {
		return Long.compare(getGeneration(), other.getGeneration());
	}

	//////////////////////////////////////////
	// Serialization methods
	// Note: this class serializes itself as a com.swirlds.common.event.ConsensusEvent object
	//////////////////////////////////////////

	/**
	 * This class serializes itself as a {@link ConsensusEvent} object
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serialize(out, EventSerializationOptions.FULL);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out, EventSerializationOptions option) throws IOException {
		ConsensusEvent.serialize(out, baseEvent.getHashedData(), baseEvent.getUnhashedData(), consensusData, option);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		ConsensusEvent consensusEvent = new ConsensusEvent();
		consensusEvent.deserialize(in, version);
		buildFromConsensusEvent(consensusEvent);

	}

	/**
	 * build current Event from consensusEvent
	 *
	 * @param consensusEvent
	 */
	void buildFromConsensusEvent(final ConsensusEvent consensusEvent) {
		baseEvent = new GossipEvent(
				consensusEvent.getBaseEventHashedData(),
				consensusEvent.getBaseEventUnhashedData()
		);
		consensusData = consensusEvent.getConsensusData();
		internalEventData = new InternalEventData();

		setDefaultValues();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return ConsensusEvent.CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ConsensusEvent.CLASS_VERSION;
	}


	//////////////////////////////////////////
	// Getters for the objects contained
	//////////////////////////////////////////

	/**
	 * @return the base event
	 */
	public GossipEvent getBaseEvent() {
		return baseEvent;
	}

	/**
	 * @return The hashed part of a base event
	 */
	public BaseEventHashedData getBaseEventHashedData() {
		return baseEvent.getHashedData();
	}

	/**
	 * @return The part of a base event which is not hashed
	 */
	public BaseEventUnhashedData getBaseEventUnhashedData() {
		return baseEvent.getUnhashedData();
	}

	/**
	 * @return Consensus data calculated for an event
	 */
	public ConsensusData getConsensusData() {
		return consensusData;
	}

	/**
	 * @return Internal data used for calculating consensus
	 */
	public InternalEventData getInternalEventData() {
		return internalEventData;
	}

	//////////////////////////////////////////
	// Convenience methods for nested objects
	//////////////////////////////////////////

	//////////////////////////////////////////
	// BaseEventHashedData
	//////////////////////////////////////////

	public Instant getTimeCreated() {
		return baseEvent.getHashedData().getTimeCreated();
	}

	public long getSelfParentGen() {
		return baseEvent.getHashedData().getSelfParentGen();
	}

	public long getOtherParentGen() {
		return baseEvent.getHashedData().getOtherParentGen();
	}

	public Hash getSelfParentHash() {
		return baseEvent.getHashedData().getSelfParentHash();
	}

	public Hash getOtherParentHash() {
		return baseEvent.getHashedData().getOtherParentHash();
	}

	public Hash getBaseHash() {
		return baseEvent.getHashedData().getHash();
	}

	/**
	 * @return array of transactions inside this event instance
	 */
	public Transaction[] getTransactions() {
		return baseEvent.getHashedData().getTransactions();
	}

	public int getNumTransactions() {
		if (baseEvent.getHashedData().getTransactions() == null) {
			return 0;
		} else {
			return baseEvent.getHashedData().getTransactions().length;
		}
	}

	public boolean isCreatedBy(NodeId id) {
		return getCreatorId() == id.getId();
	}

	public boolean isCreatedBy(long id) {
		return getCreatorId() == id;
	}

	public boolean hasUserTransactions() {
		return baseEvent.getHashedData().hasUserTransactions();
	}

	//////////////////////////////////////////
	// BaseEventUnhashedData
	//////////////////////////////////////////

	public byte[] getSignature() {
		return baseEvent.getUnhashedData().getSignature();
	}

	//////////////////////////////////////////
	// ConsensusData
	//////////////////////////////////////////

	public void setRoundCreated(long roundCreated) {
		consensusData.setRoundCreated(roundCreated);
	}

	public void setWitness(boolean witness) {
		internalEventData.setWitness(witness);
	}

	public void setFamous(boolean famous) {
		internalEventData.setFamous(famous);
	}

	public boolean isStale() {
		return consensusData.isStale();
	}

	public void setStale(boolean stale) {
		consensusData.setStale(stale);
	}

	public void setConsensusTimestamp(Instant consensusTimestamp) {
		consensusData.setConsensusTimestamp(consensusTimestamp);
	}

	public void setRoundReceived(long roundReceived) {
		consensusData.setRoundReceived(roundReceived);
	}

	public void setConsensusOrder(long consensusOrder) {
		consensusData.setConsensusOrder(consensusOrder);
	}

	/**
	 * is this event the last in consensus order of all those with the same received round
	 *
	 * @return is this event the last in consensus order of all those with the same received round
	 */
	public boolean isLastInRoundReceived() {
		return consensusData.isLastInRoundReceived();
	}

	public void setLastInRoundReceived(boolean lastInRoundReceived) {
		consensusData.setLastInRoundReceived(lastInRoundReceived);
	}

	//////////////////////////////////////////
	// InternalEventData
	//////////////////////////////////////////

	/**
	 * @param selfParent
	 * 		the self parent of this
	 */
	public void setSelfParent(EventImpl selfParent) {
		internalEventData.setSelfParent(selfParent);
	}

	/**
	 * @param otherParent
	 * 		the other parent of this
	 */
	public void setOtherParent(EventImpl otherParent) {
		internalEventData.setOtherParent(otherParent);
	}

	/**
	 * @param fameDecided
	 * 		is this both a witness and the fame election is over?
	 */
	public void setFameDecided(boolean fameDecided) {
		internalEventData.setFameDecided(fameDecided);
	}

	/**
	 * @param consensus
	 * 		is this part of the consensus order yet?
	 */
	public void setConsensus(boolean consensus) {
		internalEventData.setConsensus(consensus);
	}

	/**
	 * @return the time this event was first received locally
	 */
	public Instant getTimeReceived() {
		return internalEventData.getTimeReceived();
	}

	/**
	 * @param timeReceived
	 * 		the time this event was first received locally
	 */
	public void setTimeReceived(Instant timeReceived) {
		internalEventData.setTimeReceived(timeReceived);
	}

	/**
	 * @return an estimate of what the consensus timestamp will be (could be a very bad guess)
	 */
	public Instant getEstimatedTime() {
		return internalEventData.getEstimatedTime();
	}

	/**
	 * @param estimatedTime
	 * 		an estimate of what the consensus timestamp will be (could be a very bad guess)
	 */
	public void setEstimatedTime(Instant estimatedTime) {
		internalEventData.setEstimatedTime(estimatedTime);
	}

	/**
	 * @return the local time (not consensus time) at which the event reached consensus
	 */
	public Instant getReachedConsTimestamp() {
		return internalEventData.getReachedConsTimestamp();
	}

	/**
	 * @return has this event been cleared (because it was old and should be discarded)?
	 */
	public boolean isCleared() {
		return internalEventData.isCleared();
	}

	/**
	 * @return the Election associated with the earliest round involved in the election for this event's fame
	 */
	public RoundInfo.ElectionRound getFirstElection() {
		return internalEventData.getFirstElection();
	}

	/**
	 * @param firstElection
	 * 		the Election associated with the earliest round involved in the election for this event's fame
	 */
	public void setFirstElection(RoundInfo.ElectionRound firstElection) {
		internalEventData.setFirstElection(firstElection);
	}

	/**
	 * @return temporarily used during any graph algorithm that needs to mark vertices (events) already visited
	 */
	public int getMark() {
		return internalEventData.getMark();
	}

	/**
	 * @param mark
	 * 		temporarily used during any graph algorithm that needs to mark vertices (events) already visited
	 */
	public void setMark(int mark) {
		internalEventData.setMark(mark);
	}

	/**
	 * @return the time at which each unique famous witness in the received round first received this event
	 */
	public ArrayList<Instant> getRecTimes() {
		return internalEventData.getRecTimes();
	}

	/**
	 * @param recTimes
	 * 		the time at which each unique famous witness in the received round first received this event
	 */
	public void setRecTimes(ArrayList<Instant> recTimes) {
		internalEventData.setRecTimes(recTimes);
	}

	/**
	 * @return is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous
	 * 		witness
	 */
	public boolean isFrozen() {
		return internalEventData.isFrozen();
	}

	/**
	 * @param frozen
	 * 		is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous witness
	 */
	public void setFrozen(boolean frozen) {
		internalEventData.setFrozen(frozen);
	}

	/**
	 * @param reachedConsTimestamp
	 * 		the local time (not consensus time) at which the event reached consensus
	 */
	public void setReachedConsTimestamp(Instant reachedConsTimestamp) {
		internalEventData.setReachedConsTimestamp(reachedConsTimestamp);
	}

	/**
	 * @param m
	 * 		the member ID
	 * @return last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
	 */
	public EventImpl getLastSee(int m) {
		return internalEventData.getLastSee(m);
	}

	/**
	 * remember event, the last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
	 *
	 * @param m
	 * 		the member ID of the creator
	 * @param event
	 * 		the last seen {@link EventImpl} object created by m
	 */
	public void setLastSee(int m, EventImpl event) {
		internalEventData.setLastSee(m, event);
	}

	/**
	 * Initialize the lastSee array to hold n elements (for n &ge; 0) (memoizes lastSee function from
	 * Swirlds-TR-2020-01)
	 *
	 * @param n
	 * 		number of members in AddressBook
	 */
	public void initLastSee(int n) {
		internalEventData.initLastSee(n);
	}

	/**
	 * @return the number of elements lastSee holds (memoizes lastSee function from Swirlds-TR-2020-01)
	 */
	public int sizeLastSee() {
		return internalEventData.sizeLastSee();
	}

	/**
	 * @param m
	 * 		the member ID
	 * @return strongly-seen witness in parent round by m (memoizes stronglySeeP function from Swirlds-TR-2020-01)
	 */
	public EventImpl getStronglySeeP(int m) {
		return internalEventData.getStronglySeeP(m);
	}

	/**
	 * remember event, the strongly-seen witness in parent round by m (memoizes stronglySeeP function from
	 * Swirlds-TR-2020-01)
	 *
	 * @param m
	 * 		the member ID of the creator
	 * @param event
	 * 		the strongly-seen witness in parent round created by m
	 */
	public void setStronglySeeP(int m, EventImpl event) {
		internalEventData.setStronglySeeP(m, event);
	}

	/**
	 * Initialize the stronglySeeP array to hold n elements (for n &ge; 0) (memoizes stronglySeeP function from
	 * Swirlds-TR-2020-01)
	 *
	 * @param n
	 * 		number of members in AddressBook
	 */
	public void initStronglySeeP(int n) {
		internalEventData.initStronglySeeP(n);
	}

	/**
	 * @return the number of elements stronglySeeP holds (memoizes stronglySeeP function from Swirlds-TR-2020-01)
	 */
	public int sizeStronglySeeP() {
		return internalEventData.sizeStronglySeeP();
	}

	/**
	 * @return The first witness that's a self-ancestor in the self round (memoizes function from
	 * 		Swirlds-TR-2020-01)
	 */
	public EventImpl getFirstSelfWitnessS() {
		return internalEventData.getFirstSelfWitnessS();
	}

	/**
	 * @param firstSelfWitnessS
	 * 		The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
	 */
	public void setFirstSelfWitnessS(EventImpl firstSelfWitnessS) {
		internalEventData.setFirstSelfWitnessS(firstSelfWitnessS);
	}

	/**
	 * @return the first witness that's an ancestor in the the self round (memoizes function from
	 * 		Swirlds-TR-2020-01)
	 */
	public EventImpl getFirstWitnessS() {
		return internalEventData.getFirstWitnessS();
	}

	/**
	 * @param firstWitnessS
	 * 		the first witness that's an ancestor in the the self round (memoizes function from Swirlds-TR-2020-01)
	 */
	public void setFirstWitnessS(EventImpl firstWitnessS) {
		internalEventData.setFirstWitnessS(firstWitnessS);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isLastOneBeforeShutdown() {
		return internalEventData.isLastEventBeforeShutdown();
	}

	/**
	 * @param isLastEventBeforeShutdown
	 * 		whether this event is the last event to be written to event stream before shut down
	 */
	public void setLastOneBeforeShutdown(boolean isLastEventBeforeShutdown) {
		internalEventData.setLastEventBeforeShutdown(isLastEventBeforeShutdown);
	}

	//////////////////////////////////////////
	//	Event interface methods
	//////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isWitness() {
		return internalEventData.isWitness();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFameDecided() {
		return internalEventData.isFameDecided();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFamous() {
		return internalEventData.isFamous();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isConsensus() {
		return internalEventData.isConsensus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getConsensusTimestamp() {
		return consensusData.getConsensusTimestamp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getOtherId() {
		return baseEvent.getUnhashedData().getOtherId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventImpl getSelfParent() {
		return internalEventData.getSelfParent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventImpl getOtherParent() {
		return internalEventData.getOtherParent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getGeneration() {
		return baseEvent.getHashedData().getGeneration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getRoundCreated() {
		return consensusData.getRoundCreated();
	}

	public long getMaxRoundCreated() {
		long selfParentRound = this.getSelfParent() == null ? 0
				: this.getSelfParent().getRoundCreated();
		long otherParentRound = this.getOtherParent() == null ? 0
				: this.getOtherParent().getRoundCreated();
		return Math.max(selfParentRound, otherParentRound);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCreatorId() {
		return baseEvent.getHashedData().getCreatorId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getRoundReceived() {
		return consensusData.getRoundReceived();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getConsensusOrder() {
		return consensusData.getConsensusOrder();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Instant getTimestamp() {
		return getConsensusTimestamp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RunningHash getRunningHash() {
		return runningHash;
	}

	/**
	 * check whether this event doesn't contain any transactions
	 *
	 * @return true iff this event has no transactions
	 */
	public boolean isEmpty() {
		return getTransactions() == null || getTransactions().length == 0;
	}

	/**
	 * Check if this event was read from a signed state.
	 *
	 * @return true iff this event was loaded from a signed state
	 */
	public boolean isFromSignedState() {
		return fromSignedState;
	}

	/**
	 * Mark this as an event that was read from a signed state.
	 */
	public void markAsSignedStateEvent() {
		this.fromSignedState = true;
	}

	//
	// String methods
	//

	/**
	 * @see EventStrings#toShortString(EventImpl)
	 */
	public String toShortString() {
		return EventStrings.toShortString(this);
	}

	/**
	 * @see EventStrings#toMediumString(EventImpl)
	 */
	public String toMediumString() {
		return EventStrings.toMediumString(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return toMediumString();
	}
}

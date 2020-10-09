/*
 * (c) 2016-2020 Swirlds, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
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
import com.swirlds.platform.event.InternalEventData;
import com.swirlds.platform.internal.CreatorSeqPair;
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
		OptionalSelfSerializable<EventSerializationOptions>, Timestamped {
	/** The part of a base event that affects the hash that is signed */
	private BaseEventHashedData baseEventHashedData;
	/** The part of a base event which doesn't affect the hash that is signed */
	private BaseEventUnhashedData baseEventUnhashedData;
	/** Consensus data calculated for an event (doesn't affect the hash that is signed) */
	private ConsensusData consensusData;
	/** Internal data used for calculating consensus (doesn't affect the hash that is signed) */
	private InternalEventData internalEventData;
	/** does this event contain only some of the information? (doesn't affect the hash that is signed) */
	private boolean abbreviatedStateEvent;
	/** a pair of values used to identify this event (doesn't affect the hash that is signed) */
	private CreatorSeqPair creatorSeqPair;

	public EventImpl() {
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData) {
		this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), null, null);
		calculateGeneration();
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final ConsensusData consensusData) {
		this(baseEventHashedData, baseEventUnhashedData, consensusData, null, null);
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), selfParent, otherParent);
		calculateGeneration();
	}

	/**
	 * This constructor is used in {@link StreamEventParser} when parsing events from stream
	 *
	 * @param consensusEvent
	 */
	EventImpl(final ConsensusEvent consensusEvent) {
		buildFromConsensusEvent(consensusEvent);
	}

	public EventImpl(final BaseEventHashedData baseEventHashedData,
			final BaseEventUnhashedData baseEventUnhashedData,
			final ConsensusData consensusData,
			final EventImpl selfParent,
			final EventImpl otherParent) {
		CommonUtils.throwArgNull(baseEventHashedData, "baseEventDataHashed");
		CommonUtils.throwArgNull(baseEventUnhashedData, "baseEventDataNotHashed");
		CommonUtils.throwArgNull(consensusData, "consensusData");

		this.baseEventHashedData = baseEventHashedData;
		this.baseEventUnhashedData = baseEventUnhashedData;
		this.consensusData = consensusData;
		this.internalEventData = new InternalEventData(selfParent, otherParent);

		abbreviatedStateEvent = false;
		EventCounter.eventCreated();

		setDefaultValues();
	}

	/**
	 * Set the creatorSeqPair to be the pair of the creator ID and the creator sequence number.
	 */
	private void setDefaultValues() {
		creatorSeqPair = new CreatorSeqPair(baseEventHashedData.getCreatorId(), baseEventUnhashedData.getCreatorSeq());
	}

	/**
	 * Set this event's generation to be 1 more than the max of its parents.
	 */
	public void calculateGeneration() {
		consensusData.setGeneration(
				1 + Math.max(
						baseEventHashedData.getSelfParentGen(),
						baseEventHashedData.getOtherParentGen()));
	}

	/**
	 * Calls {@link InternalEventData#clear()}
	 */
	void clear() {
		internalEventData.clear();
	}

	/**
	 * set the consensusTimestamp to an estimate of what it will be when consensus is reached. If it already
	 * has consensus, then do nothing.
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
	synchronized void estimateTime(NodeId selfId, double avgSelfCreatedTimestamp, double avgOtherReceivedTimestamp) {
		/* a base time */
		Instant t;
		/* number of seconds to add to the base time */
		double sec;

		if (isConsensus()) {
			return;
		}
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

		setConsensusTimestamp(t.plus((long) (sec * 1_000_000_000.0), ChronoUnit.NANOS));
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


	public Instant getTransactionTime(int transactionIndex) {
		if (getConsensusTimestamp() == null || getTransactions() == null) {
			return null;
		}
		if (transactionIndex >= getTransactions().length) {
			throw new IllegalArgumentException("Event does not have a transaction with index:" + transactionIndex);
		}
		return getConsensusTimestamp().plusNanos(transactionIndex);
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
				.append(abbreviatedStateEvent, event.abbreviatedStateEvent)
				.append(baseEventHashedData, event.baseEventHashedData)
				.append(baseEventUnhashedData, event.baseEventUnhashedData)
				.append(consensusData, event.consensusData)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(abbreviatedStateEvent)
				.append(baseEventHashedData)
				.append(baseEventUnhashedData)
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "eventImpl{" +
				"baseEventHashedData=" + baseEventHashedData +
				", baseEventUnhashedData=" + baseEventUnhashedData +
				", consensusData=" + consensusData +
				", internalEventData=" + internalEventData +
				", abbreviatedStateEvent=" + abbreviatedStateEvent +
				'}';
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
		ConsensusEvent.serialize(out, baseEventHashedData, baseEventUnhashedData, consensusData, option);
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
		baseEventHashedData = consensusEvent.getBaseEventHashedData();
		baseEventUnhashedData = consensusEvent.getBaseEventUnhashedData();
		consensusData = consensusEvent.getConsensusData();
		internalEventData = new InternalEventData();

		setDefaultValues();
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public long getClassId() {
		return ConsensusEvent.CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public int getVersion() {
		return ConsensusEvent.CLASS_VERSION;
	}


	//////////////////////////////////////////
	// Getters for the objects contained
	//////////////////////////////////////////

	/**
	 * @return The hashed part of a base event
	 */
	@JsonIgnore
	public BaseEventHashedData getBaseEventHashedData() {
		return baseEventHashedData;
	}

	/**
	 * @return The part of a base event which is not hashed
	 */
	@JsonIgnore
	public BaseEventUnhashedData getBaseEventUnhashedData() {
		return baseEventUnhashedData;
	}

	/**
	 * @return Consensus data calculated for an event
	 */
	@JsonIgnore
	public ConsensusData getConsensusData() {
		return consensusData;
	}

	/**
	 * @return Internal data used for calculating consensus
	 */
	@JsonIgnore
	public InternalEventData getInternalEventData() {
		return internalEventData;
	}

	/**
	 * @return does this event contain only some of the information?
	 */
	public boolean isAbbreviatedStateEvent() {
		return abbreviatedStateEvent;
	}

	/**
	 * @param abbreviatedStateEvent
	 * 		does this event contain only some of the information?
	 */
	public void setAbbreviatedStateEvent(boolean abbreviatedStateEvent) {
		this.abbreviatedStateEvent = abbreviatedStateEvent;
	}

	/**
	 * @return a pair of values used to identify this event
	 */
	public CreatorSeqPair getCreatorSeqPair() {
		return creatorSeqPair;
	}

	//////////////////////////////////////////
	// Convenience methods for nested objects
	//////////////////////////////////////////

	//////////////////////////////////////////
	// BaseEventHashedData
	//////////////////////////////////////////

	public Instant getTimeCreated() {
		return baseEventHashedData.getTimeCreated();
	}

	public long getSelfParentGen() {
		return baseEventHashedData.getSelfParentGen();
	}

	public long getOtherParentGen() {
		return baseEventHashedData.getOtherParentGen();
	}

	public Hash getSelfParentHash() {
		return baseEventHashedData.getSelfParentHash();
	}

	public Hash getOtherParentHash() {
		return baseEventHashedData.getOtherParentHash();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash getBaseHash() {
		return baseEventHashedData.getHash();
	}

	@JsonIgnore
	public Transaction[] getTransactions() {
		return baseEventHashedData.getTransactions();
	}

	public boolean hasUserTransactions() {
		return baseEventHashedData.hasUserTransactions();
	}

	//////////////////////////////////////////
	// BaseEventUnhashedData
	//////////////////////////////////////////

	public byte[] getSignature() {
		return baseEventUnhashedData.getSignature();
	}

	//////////////////////////////////////////
	// ConsensusData
	//////////////////////////////////////////

	public void setGeneration(long generation) {
		consensusData.setGeneration(generation);
	}

	public void setRoundCreated(long roundCreated) {
		consensusData.setRoundCreated(roundCreated);
	}

	public void setWitness(boolean witness) {
		consensusData.setWitness(witness);
	}

	public void setFamous(boolean famous) {
		consensusData.setFamous(famous);
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
	@JsonIgnore
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
	 * @return the local time (not consensus time) at which the event reached consensus
	 */
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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
	@JsonIgnore
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

	/**
	 * @return the running hash of all consensus events in history, up through this event
	 */
	public Hash getRunningHash() {
		return internalEventData.getRunningHash();
	}

	/**
	 * @param runningHash
	 * 		the running hash of all consensus events in history, up through this event
	 */
	public void setRunningHash(Hash runningHash) {
		internalEventData.setRunningHash(runningHash);
	}

	/**
	 * @return n-1 for the nth witness added to a round (-1 if not a witness. Can be different on different computers)
	 */
	public int getWitnessSeq() {
		return internalEventData.getWitnessSeq();
	}

	/**
	 * @param witnessSeq
	 * 		n-1 for the nth witness added to a round (-1 if not a witness. Can be different on different computers)
	 */
	public void setWitnessSeq(int witnessSeq) { internalEventData.setWitnessSeq(witnessSeq); }

	//////////////////////////////////////////
	//	Event interface methods
	//////////////////////////////////////////

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isWitness() {
		return consensusData.isWitness();
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
		return consensusData.isFamous();
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
		return baseEventUnhashedData.getOtherId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCreatorSeq() {
		return baseEventUnhashedData.getCreatorSeq();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getOtherSeq() {
		return baseEventUnhashedData.getOtherSeq();
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public EventImpl getSelfParent() {
		return internalEventData.getSelfParent();
	}

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public EventImpl getOtherParent() {
		return internalEventData.getOtherParent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getGeneration() {
		return consensusData.getGeneration();
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
		return baseEventHashedData.getCreatorId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getSeq() {
		return baseEventUnhashedData.getCreatorSeq();
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

	@Override
	public Instant getTimestamp() {
		return getConsensusTimestamp();
	}
}

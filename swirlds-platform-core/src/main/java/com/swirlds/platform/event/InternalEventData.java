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

package com.swirlds.platform.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.RoundInfo;

import java.time.Instant;
import java.util.ArrayList;

/**
 * A class that stores temporary data that is used while calculating consensus inside the platform. This data is not
 * relevant after consensus has been calculated.  It does not affect the hash that is signed.
 */
public class InternalEventData {
	/** the self parent of this */
	@JsonIgnore
	private EventImpl selfParent;
	/** the other parent of this */
	@JsonIgnore
	private EventImpl otherParent;
	/** the time this event was first received locally */
	@JsonIgnore
	private Instant timeReceived;
	/** has this event been cleared (because it was old and should be discarded)? */
	private boolean cleared = false;
	/** is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous witness */
	private boolean isFrozen = false;
	/** is this both a witness and the fame election is over? */
	private boolean isFameDecided;
	/** is this part of the consensus order yet? */
	private boolean isConsensus;
	/** the local time (not consensus time) at which the event reached consensus */
	@JsonIgnore
	private Instant reachedConsTimestamp;
	/** the Election associated with the earliest round involved in the election for this event's fame */
	@JsonIgnore
	private RoundInfo.ElectionRound firstElection;
	/** n-1 for the nth witness added to a round (-1 if not a witness. Can be different on different computers) */
	@JsonIgnore
	private int witnessSeq = -1;
	/** does this event contains user transactions (not just system transactions) */
	private boolean hasUserTransactions = false;
	/** lastSee[m] is the last ancestor created by m (memoizes function from Swirlds-TR-2020-01) */
	@JsonIgnore
	private EventImpl[] lastSee;
	/** stronglySeeP[m] is strongly-seen witness in parent round by m (memoizes function from Swirlds-TR-2020-01) */
	@JsonIgnore
	private EventImpl[] stronglySeeP;
	/** The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01) */
	@JsonIgnore
	private EventImpl firstSelfWitnessS;
	/** the first witness that's an ancestor in the the self round (memoizes function from Swirlds-TR-2020-01) */
	@JsonIgnore
	private EventImpl firstWitnessS;
	/** temporarily used during any graph algorithm that needs to mark vertices (events) already visited */
	@JsonIgnore
	private int mark;
	/** the time at which each unique famous witness in the received round first received this event */
	private ArrayList<Instant> recTimes;

	/**
	 * only used for event streaming
	 * this is the last event to be written to event stream before restart
	 */
	@JsonIgnore
	private boolean isLastEventBeforeShutdown = false;

	/** a running hash of all consensus events in history, up through this event */
	@JsonIgnore
	private Hash runningHash;

	public InternalEventData() {
		this.timeReceived = Instant.now();
		this.mark = 0; //ConsensusImpl.currMark starts at 1 and counts up, so all events initially count as unmarked
	}

	public InternalEventData(EventImpl selfParent, EventImpl otherParent) {
		this();
		this.selfParent = selfParent;
		this.otherParent = otherParent;
	}

	@Override
	public String toString() {
		return "InternalEventData{" +
				"selfParent=" + EventUtils.toShortString(selfParent) +
				", otherParent=" + EventUtils.toShortString(otherParent) +
				", timeReceived=" + timeReceived +
				", cleared=" + cleared +
				", isFrozen=" + isFrozen +
				", isFameDecided=" + isFameDecided +
				", isConsensus=" + isConsensus +
				", reachedConsTimestamp=" + reachedConsTimestamp +
				", firstElection=" + firstElection +
				", hasUserTransactions=" + hasUserTransactions +
				", lastSee=" + EventUtils.toShortStrings(lastSee) +
				", stronglySeeP=" + EventUtils.toShortStrings(stronglySeeP) +
				", firstSelfWitnessS=" + EventUtils.toShortString(firstSelfWitnessS) +
				", firstWitnessS=" + EventUtils.toShortString(firstWitnessS) +
				", mark=" + mark +
				", recTimes=" + recTimes +
				'}';
	}

	/**
	 * Erase all references to other events within this event. This can be used so other events can be
	 * garbage collected, even if this one still has things pointing to it. The numEventsInMemory count is
	 * decremented here, and incremented when the event is instantiated, so it is important to ensure that
	 * this is eventually called on every event.
	 */
	public void clear() {
		cleared = true;
		EventCounter.eventCleared();
		selfParent = null;
		otherParent = null;
		initLastSee(0);
		initStronglySeeP(0);
		firstSelfWitnessS = null;
		firstWitnessS = null;
	}


	//////////////////////////////////////////
	// Getters and setters
	//////////////////////////////////////////

	/**
	 * @return the self parent of this
	 */
	public EventImpl getSelfParent() {
		return selfParent;
	}

	/**
	 * @param selfParent
	 * 		the self parent of this
	 */
	public void setSelfParent(EventImpl selfParent) {
		this.selfParent = selfParent;
	}

	/**
	 * @return the other parent of this
	 */
	public EventImpl getOtherParent() {
		return otherParent;
	}

	/**
	 * @param otherParent
	 * 		the other parent of this
	 */
	public void setOtherParent(EventImpl otherParent) {
		this.otherParent = otherParent;
	}

	/**
	 * @return the time this event was first received locally
	 */
	public Instant getTimeReceived() {
		return timeReceived;
	}

	/**
	 * @param timeReceived
	 * 		the time this event was first received locally
	 */
	public void setTimeReceived(Instant timeReceived) {
		this.timeReceived = timeReceived;
	}

	/**
	 * @return has this event been cleared (because it was old and should be discarded)?
	 */
	public boolean isCleared() {
		return cleared;
	}

	/**
	 * @param cleared
	 * 		has this event been cleared (because it was old and should be discarded)?
	 */
	public void setCleared(boolean cleared) {
		this.cleared = cleared;
	}

	/**
	 * @return is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous
	 * 		witness
	 */
	public boolean isFrozen() {
		return isFrozen;
	}

	/**
	 * @param frozen
	 * 		is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous witness
	 */
	public void setFrozen(boolean frozen) {
		isFrozen = frozen;
	}

	/**
	 * @return is this both a witness and the fame election is over?
	 */
	public boolean isFameDecided() {
		return isFameDecided;
	}

	/**
	 * @param fameDecided
	 * 		is this both a witness and the fame election is over?
	 */
	public void setFameDecided(boolean fameDecided) {
		isFameDecided = fameDecided;
	}

	/**
	 * @return is this part of the consensus order yet?
	 */
	public boolean isConsensus() {
		return isConsensus;
	}

	/**
	 * @param consensus
	 * 		is this part of the consensus order yet?
	 */
	public void setConsensus(boolean consensus) {
		isConsensus = consensus;
	}

	/**
	 * @return the local time (not consensus time) at which the event reached consensus
	 */
	public Instant getReachedConsTimestamp() {
		return reachedConsTimestamp;
	}

	/**
	 * @param reachedConsTimestamp
	 * 		the local time (not consensus time) at which the event reached consensus
	 */
	public void setReachedConsTimestamp(Instant reachedConsTimestamp) {
		this.reachedConsTimestamp = reachedConsTimestamp;
	}

	/**
	 * @return the Election associated with the earliest round involved in the election for this event's fame
	 */
	public RoundInfo.ElectionRound getFirstElection() {
		return firstElection;
	}

	/**
	 * @param firstElection
	 * 		the Election associated with the earliest round involved in the election for this event's fame
	 */
	public void setFirstElection(RoundInfo.ElectionRound firstElection) {
		this.firstElection = firstElection;
	}

	/**
	 * @return does this event contains user transactions (not just system transactions)
	 */
	public boolean hasUserTransactions() {
		return hasUserTransactions;
	}

	/**
	 * @param hasUserTransactions
	 * 		does this event contains user transactions (not just system transactions)
	 */
	public void setHasUserTransactions(boolean hasUserTransactions) {
		this.hasUserTransactions = hasUserTransactions;
	}


	/**
	 * @return last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
	 */
	public EventImpl getLastSee(int m) {
		return lastSee[m];
	}

	/**
	 * remember event, the last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
	 *
	 * @param m
	 * @param event
	 */
	public void setLastSee(int m, EventImpl event) {
		lastSee[m] = event;
	}

	/**
	 * Initialize the lastSee array to hold n elements (for n &ge; 0) (memoizes lastSee function from
	 * Swirlds-TR-2020-01)
	 */
	public void initLastSee(int n) {
		lastSee = n == 0 ? null : new EventImpl[n];
	}

	/**
	 * @return the number of elements lastSee holds (memoizes lastSee function from Swirlds-TR-2020-01)
	 */
	public int sizeLastSee() {
		return lastSee == null ? 0 : lastSee.length;
	}

	/**
	 * @return strongly-seen witness in parent round by m (memoizes stronglySeeP function from Swirlds-TR-2020-01)
	 */
	public EventImpl getStronglySeeP(int m) {
		return stronglySeeP[m];
	}

	/**
	 * remember event, the strongly-seen witness in parent round by m (memoizes stronglySeeP function from
	 * Swirlds-TR-2020-01)
	 *
	 * @param m
	 * @param event
	 */
	public void setStronglySeeP(int m, EventImpl event) {
		stronglySeeP[m] = event;
	}

	/**
	 * Initialize the stronglySeeP array to hold n elements (for n &ge; 0) (memoizes stronglySeeP function from
	 * Swirlds-TR-2020-01)
	 */
	public void initStronglySeeP(int n) {
		stronglySeeP = n == 0 ? null : new EventImpl[n];
	}

	/**
	 * @return the number of elements stronglySeeP holds (memoizes stronglySeeP function from Swirlds-TR-2020-01)
	 */
	public int sizeStronglySeeP() {
		return stronglySeeP == null ? 0 : stronglySeeP.length;
	}

	/**
	 * @return The first witness that's a self-ancestor in the self round (memoizes function from
	 * 		Swirlds-TR-2020-01)
	 */
	public EventImpl getFirstSelfWitnessS() {
		return firstSelfWitnessS;
	}

	/**
	 * @param firstSelfWitnessS
	 * 		The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
	 */
	public void setFirstSelfWitnessS(EventImpl firstSelfWitnessS) {
		this.firstSelfWitnessS = firstSelfWitnessS;
	}

	/**
	 * @return the first witness that's an ancestor in the the self round (memoizes function from
	 * 		Swirlds-TR-2020-01)
	 */
	public EventImpl getFirstWitnessS() {
		return firstWitnessS;
	}

	/**
	 * @param firstWitnessS
	 * 		the first witness that's an ancestor in the the self round (memoizes function from Swirlds-TR-2020-01)
	 */
	public void setFirstWitnessS(EventImpl firstWitnessS) {
		this.firstWitnessS = firstWitnessS;
	}

	/**
	 * @return temporarily used during any graph algorithm that needs to mark vertices (events) already visited
	 */
	public int getMark() {
		return mark;
	}

	/**
	 * @param mark
	 * 		temporarily used during any graph algorithm that needs to mark vertices (events) already visited
	 */
	public void setMark(int mark) {
		this.mark = mark;
	}

	/**
	 * @return the time at which each unique famous witness in the received round first received this event
	 */
	public ArrayList<Instant> getRecTimes() {
		return recTimes;
	}

	/**
	 * @param recTimes
	 * 		the time at which each unique famous witness in the received round first received this event
	 */
	public void setRecTimes(ArrayList<Instant> recTimes) {
		this.recTimes = recTimes;
	}

	/**
	 * @return whether this event is the last event to be written to event stream before shut down
	 */
	public boolean isLastEventBeforeShutdown() {
		return isLastEventBeforeShutdown;
	}

	/**
	 * @param isLastEventBeforeShutdown
	 * 		whether this event is the last event to be written to event stream before shut down
	 */
	public void setLastEventBeforeShutdown(boolean isLastEventBeforeShutdown) {
		this.isLastEventBeforeShutdown = isLastEventBeforeShutdown;
	}

	/**
	 * @return the running hash of all consensus events in history, up through this event
	 */
	public Hash getRunningHash() {
		return runningHash;
	}

	/**
	 * @param runningHash
	 * 		the running hash of all consensus events in history, up through this event
	 */
	public void setRunningHash(Hash runningHash) {
		this.runningHash = runningHash;
	}

	/**
	 * @return n-1 for the nth witness added to a round (-1 if not a witness. Can be different on different computers)
	 */
	public int getWitnessSeq() {
		return witnessSeq;
	}

	/**
	 * @param witnessSeq
	 * 		n-1 for the nth witness added to a round (-1 if not a witness. Can be different on different computers)
	 */
	public void setWitnessSeq(int witnessSeq) {
		this.witnessSeq = witnessSeq;
	}
}

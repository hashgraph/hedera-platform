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

package com.swirlds.platform.components;

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.observers.ConsensusEventObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import com.swirlds.platform.observers.StaleEventObserver;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks user transactions that have not reached consensus, as well as the round at which the last one reached
 * consensus.
 */
public class TransactionTracker implements EventAddedObserver, ConsensusEventObserver, StaleEventObserver {
	/**
	 * the number of non-consensus, non-stale events with user transactions currently in the hashgraph
	 */
	private final AtomicLong numUserTransEvents;

	/**
	 * the last round received with at least 1 user transaction
	 */
	private volatile long lastRRwithUserTransaction;

	/**
	 * the last round received after which there were no non consensus user transactions in the hashgraph, but before
	 * this round there were
	 */
	private volatile long lastRoundReceivedAllTransCons;

	/**
	 * Default-construct a {@code TransactionTracker} instance. The resulting instance
	 * contains no transaction information, and returns a round number of -1.
	 */
	public TransactionTracker() {
		numUserTransEvents = new AtomicLong(0);
		reset();
	}

	/**
	 * Reset this instance to its constructed state.
	 */
	public void reset() {
		numUserTransEvents.set(0);
		lastRoundReceivedAllTransCons = -1;
		lastRRwithUserTransaction = -1;
	}

	/**
	 * Notifies that tracker that an event is being added to the hashgraph
	 *
	 * @param event
	 * 		the event being added
	 */
	@Override
	public void eventAdded(EventImpl event) {
		// check if this event has user transactions, if it does, increment the counter
		if (event.hasUserTransactions()) {
			numUserTransEvents.incrementAndGet();
		}
	}

	@Override
	public void consensusEvent(EventImpl event) {
		// check if this event has user transactions, if it does, decrement the counter
		if (event.hasUserTransactions()) {
			numUserTransEvents.decrementAndGet();
			lastRRwithUserTransaction = event.getRoundReceived();
			// we decrement the numUserTransEvents for every event that has user transactions. If the counter
			// reaches 0, we keep this value of round received
			if (numUserTransEvents.get() == 0) {
				lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
			}
		}
	}

	/**
	 * Notifies that tracker that an event has been declared stale
	 *
	 * @param event
	 * 		the event being added
	 */
	@Override
	public void staleEvent(EventImpl event) {
		if (event.hasUserTransactions()) {
			numUserTransEvents.decrementAndGet();
			if (numUserTransEvents.get() == 0) {
				lastRoundReceivedAllTransCons = lastRRwithUserTransaction;
			}
		}
	}

	/**
	 * @return the number of events with user transactions currently in the hashgraph that have not reached consensus
	 */
	public long getNumUserTransEvents() {
		return numUserTransEvents.get();
	}

	/**
	 * @return the last round received after which there were no non consensus user transactions in the hashgraph
	 */
	public long getLastRoundReceivedAllTransCons() {
		return lastRoundReceivedAllTransCons;
	}

	/**
	 * Update the last round-received which included at least one user (non-system) transaction.
	 *
	 * @param lastRRwithUserTransaction
	 * 		the new value of the last such round
	 */
	public void setLastRRwithUserTransaction(long lastRRwithUserTransaction) {
		this.lastRRwithUserTransaction = lastRRwithUserTransaction;
	}
}

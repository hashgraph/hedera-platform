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

package com.swirlds.platform;

import com.swirlds.platform.components.SignatureExpander;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.components.TransactionPool;
import com.swirlds.platform.components.TransactionSupplier;
import com.swirlds.platform.observers.ConsensusEventObserver;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import com.swirlds.platform.state.PlatformDualState;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.BlockingQueue;

import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;

abstract class AbstractEventFlow implements
		PreConsensusEventObserver,
		ConsensusEventObserver,
		SignatureExpander,
		TransactionSupplier,
		TransactionPool,
		FreezePeriodChecker,
		TransThrottleSyncAndCreateRule {

	/** add the given event to the forCurr queue, blocking if it is full */
	abstract void forCurrPut(EventImpl event);

	/**
	 * Return the last state of the app that has reached consensus.
	 *
	 * @return the last app state to have reached consensus
	 */
	abstract State getConsensusState();

	/**
	 * Add information about the minimum generation of all famous witnesses for a particular round
	 *
	 * @param round
	 * 		the round created
	 * @param minGeneration
	 * 		the minimum generation
	 */
	abstract void addMinGenInfo(long round, long minGeneration);

	/**
	 * @return the number of events in the forCurr queue
	 */
	abstract int getForCurrSize();

	/**
	 * @return return the number of events in the forSigs queue
	 */
	abstract int getForSigsSize();

	/**
	 * @return the number of events in the forCons queue
	 */
	abstract int getForConsSize();

	/**
	 * @return the number of signed states in the stateToHashSign queue
	 */
	abstract int getStateToHashSignSize();

	/**
	 * @return a list of transactions by self
	 */
	abstract TransLists getTransLists();

	/**
	 * Stops all the threads in EventFlow and clears out all the data
	 */
	abstract void stopAndClear();

	/**
	 * Set the {@link State} to be used by {@link EventFlow}. This should be called on an object that
	 * has had {@link #stopAndClear()} previously called on it.
	 *
	 * @param state
	 * 		the state to be set
	 */
	abstract void setState(State state);


	/**
	 * Loads data from a SignedState, this is used on startup to load events and the running hash that have
	 * been previously saved on disk
	 *
	 * @param signedState
	 * 		the state to load data from
	 * @param isReconnect
	 * 		if it is true, the signedState is loaded at reconnect;
	 * 		if it is false, the signedState is loaded at startup
	 */
	abstract void loadDataFromSignedState(SignedState signedState, boolean isReconnect);

	/**
	 * Call start on the 3 threads managing the 3 event queues and the background hashing and signing thread. Each
	 * thread is an infinite loop that simply calls the given method repeatedly.
	 */
	abstract void startAll();

	/**
	 * @return queue of events to send to stateCurr.
	 */
	abstract BlockingQueue<EventImpl> getForCurr();

	/**
	 * {@inheritDoc}
	 */
	public boolean isInFreezePeriod(Instant consensusTime) {
		final PlatformDualState dualState = getConsensusState().getPlatformDualState();
		return dualState != null && dualState.isInFreezePeriod(consensusTime);
	}

	/**
	 * {@inheritDoc}
	 */
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		// if we have transactions waiting to be put into an event, initiate a sync
		// if current time is 1 minute before or during the freeze period, initiate a sync
		if (numUserTransForEvent() > 0 || isInFreezePeriod(
				Instant.now().plus(1, ChronoUnit.MINUTES)) ||
				isInFreezePeriod(Instant.now())) {
			return SYNC_AND_CREATE;
		} else {
			return PASS;
		}
	}
}

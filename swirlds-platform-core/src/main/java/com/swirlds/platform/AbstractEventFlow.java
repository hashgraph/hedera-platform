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

import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.platform.state.SignedState;

import java.util.concurrent.BlockingQueue;

abstract class AbstractEventFlow {
	/**
	 * Add the given event (which just became consensus) to the forCons queue, to later be sent to
	 * stateCons.
	 * <p>
	 * When Hashgraph first finds consensus for an event, it puts the event into {@link RunningHashCalculator}'s queue, which will then call this method.
	 * If the queue is full, then this will block until it isn't full, which will block whatever thread called Hashgraph.consRecordEvent.
	 * <p>
	 * Thread interruptions are ignored.
	 *
	 * @param event
	 * 		the new event to record
	 */
	abstract void forConsPut(EventImpl event);

	/** add the given event to the forCurr queue, blocking if it is full */
	abstract void forCurrPut(EventImpl event);

	/**
	 * @return the number of user transactions waiting to be put in an event
	 */
	abstract int numUserTransForEvent();

	/** remove the list of transactions from the the transLists, so they can be put into a new Event */
	abstract Transaction[] pollTransListsForEvent();

	/**
	 * Return the last state of the app that has reached consensus.
	 *
	 * @return the last app state to have reached consensus
	 */
	abstract SwirldState getConsensusState();

	/**
	 * Add information about the minimum generation of all famous witnesses for a particular round
	 *
	 * @param round
	 * 		the round created
	 * @param minGeneration
	 * 		the minimum generation
	 */
	abstract void addMinGenInfo(long round, long minGeneration);

	abstract int numFreezeTransEvent();

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
	 * @return a list of transactions by self
	 */
	abstract TransLists getTransLists();

	/**
	 * Stops all the threads in EventFlow and clears out all the data
	 */
	abstract void stopAndClear();

	/**
	 * Set the {@link SwirldState} SwirldState to be used by {@link EventFlow}. This should be called on an object that
	 * has had {@link #stopAndClear()} previously called on it.
	 *
	 * @param state
	 * 		the state to be set
	 */
	abstract void setState(SwirldState state);


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
}

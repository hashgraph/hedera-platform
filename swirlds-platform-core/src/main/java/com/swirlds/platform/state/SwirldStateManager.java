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

package com.swirlds.platform.state;

import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.FreezePeriodChecker;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.eventhandling.EventTransactionPool;

import java.time.Instant;

/**
 * The methods used to interact with instances of {@link SwirldState}.
 */
public interface SwirldStateManager extends FreezePeriodChecker, TransThrottleSyncAndCreateRule {

	/**
	 * Passes all non-system transactions to {@link SwirldState#expandSignatures(SwirldTransaction)} for signature
	 * expansion. Invoked prior to adding the event to the queue for pre-consensus handling.
	 *
	 * @param event
	 */
	void expandSignatures(final EventImpl event);

	/**
	 * Handles an event before it reaches consensus. Implementations are responsible for passing each to
	 * {@link SwirldState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction,
	 * SwirldDualState)} with {@code consensus} equal to {@code false}.
	 *
	 * @param event
	 * 		the event to handle
	 */
	void handlePreConsensusEvent(final EventImpl event);

	/**
	 * Provides a {@link Runnable} to execute while waiting for pre-consensus events (q1) to process.
	 *
	 * @return the runnable
	 */
	default InterruptableRunnable getPreConsensusWaitForWorkRunnable() {
		// tells QueueThread to execute its default method when there is nothing in the queue to process
		return null;
	}

	/**
	 * Provides a {@link Runnable} to execute while waiting for consensus events (q2) to process.
	 *
	 * @return the runnable
	 */
	default InterruptableRunnable getConsensusWaitForWorkRunnable() {
		// tells QueueThread to execute its default method when there is nothing in the queue to process
		return null;
	}

	/**
	 * Determines if a pre-consensus event should be discarded or added to the pre-consensus queue (q1) for processing.
	 *
	 * @param event
	 * 		the event to discard or not
	 * @return true if the event should be discarded, false otherwise
	 */
	default boolean discardPreConsensusEvent(final EventImpl event) {
		return false;
	}

	/**
	 * Provides the transaction pool used to store transactions submitted by this node.
	 *
	 * @return the transaction pool
	 */
	EventTransactionPool getTransactionPool();

	/**
	 * Handles the events in a consensus round. Implementations are responsible for invoking {@link
	 * SwirldState#handleTransaction(long, boolean, Instant, Instant, SwirldTransaction,
	 * SwirldDualState)} on all user transactions in the contained events with {@code consensus}
	 * equal to {@code true}.
	 *
	 * @param round
	 */
	void handleConsensusRound(final ConsensusRound round);

	/**
	 * <p>Updates the state to a fast copy of itself and returns a reference to the previous state to be used for
	 * signing. The reference count of the previous state returned by this is incremented to prevent it from being
	 * garbage collected until it is put in a signed state, so callers are responsible for decrementing the reference
	 * count when it is no longer needed.</p>
	 *
	 * <p>Consensus event handling will block until this method returns. Pre-consensus
	 * event handling may or may not be blocked depending on the implementation.</p>
	 *
	 * @return a copy of the state to use for the next signed state
	 * @see State#copy()
	 */
	State getStateForSigning();

	/**
	 * Invoked when a signed state is about to be created for the current freeze period.
	 * <p>
	 * Invoked only by the consensus handling thread, so there is no chance of the state being modified by a concurrent
	 * thread.
	 * </p>
	 */
	void savedStateInFreezePeriod();

	/**
	 * Sets the state. Should be called after a state is received via reconnect.
	 *
	 * @param state
	 * 		the new state to use
	 */
	void setState(final State state);

	/**
	 * Return the current state of the app. It changes frequently, so this needs to be called frequently. This method
	 * also guarantees that the state will not be deleted until {@link #releaseCurrentSwirldState()} is invoked.
	 *
	 * @return the current app state
	 */
	SwirldState getCurrentSwirldState();

	/**
	 * Returns the consensus state. The consensus state could become immutable at any time. Modifications must not be
	 * made to the returned state.
	 */
	State getConsensusState();

	/**
	 * Releases the state that was previously returned, so that another one can be obtained from {@link
	 * #getCurrentSwirldState()}, and deletes it if it's not the current state being used.
	 */
	default void releaseCurrentSwirldState() {
		// default is NO-OP
	}

	/**
	 * Performs any actions necessary to prepare for reconnect. This may include stopping threads, clearing queues,
	 * releasing references to state, etc.
	 *
	 * @throws InterruptedException
	 * 		if this thread is interrupted
	 */
	void prepareForReconnect() throws InterruptedException;

	/**
	 * <p>Submits a self transaction for any necessary processing separate from the transaction's propagation to the
	 * network. A transaction must only be submitted here if it is also submitted for network propagation in {@link
	 * EventTransactionPool}.</p>
	 *
	 * @param transaction
	 * 		the transaction to submit
	 */
	boolean submitTransaction(final Transaction transaction);


	/**
	 * Called during recovery. Updates the dual state status to clear any possible inconsistency between freezeTime and
	 * lastFrozenTime.
	 */
	void clearFreezeTimes();

	/**
	 * Indicates is the threads applying transactions to the state can be interrupted.
	 *
	 * @return true if the threads can be interrupted
	 */
	default boolean isInterruptable() {
		return false;
	}
}

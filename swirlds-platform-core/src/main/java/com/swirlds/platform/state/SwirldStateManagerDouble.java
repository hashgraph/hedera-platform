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

import com.swirlds.common.NodeId;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.SignatureExpander;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * <p>Manages all interactions with the 3 state objects required by {@link SwirldState.SwirldState2}.</p>
 *
 * <p>Two threads modify states in this class: pre-consensus event handler and consensus event handler. Transactions
 * are submitted by a different thread. Other threads can access parts of the states by calling
 * {@link #getCurrentSwirldState()} and {@link #getConsensusState()}. Sync threads access state to check if there is
 * an active freeze period. Careful attention must be paid to changes in this class regarding locking and
 * synchronization in this class and its utility classes.</p>
 */
public class SwirldStateManagerDouble implements SwirldStateManager {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** Stats relevant to SwirldState operations. */
	private final SwirldStateStats stats;

	/** reflects all known, pre-consensus and consensus transactions */
	private volatile StateInfo stateCurrAndCons;

	/** Contains self transactions to be included in the next event. */
	private final EventTransactionPool transactionPool;

	/** Handle transactions by applying them to a state */
	private final TransactionHandler eventHandler;

	/** Expands signatures on pre-consensus transactions. */
	private final SignatureExpander signatureExpander;

	// Used of creating mock instances in unit testing
	public SwirldStateManagerDouble() {
		stats = null;
		transactionPool = null;
		eventHandler = null;
		signatureExpander = null;
	}

	/**
	 * Creates a new instance with the provided state.
	 *
	 * @param selfId
	 * 		this node's id
	 * @param systemTransactionHandler
	 * 		the handler for system transactions
	 * @param stats
	 * 		statistics relevant to this class
	 * @param initialState
	 * 		the initial state of this application
	 */
	public SwirldStateManagerDouble(
			final NodeId selfId,
			final SystemTransactionHandler systemTransactionHandler,
			final SwirldStateStats stats,
			final SettingsProvider settings,
			final SignatureExpander signatureExpander,
			final State initialState) {
		this.stats = stats;
		this.signatureExpander = signatureExpander;
		this.transactionPool = new EventTransactionPool(settings);
		this.eventHandler = new TransactionHandler(selfId, systemTransactionHandler, stats);
		setState(initialState);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean submitTransaction(final Transaction transaction) {
		return transactionPool.submitTransaction(transaction);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handlePreConsensusEvent(final EventImpl event) {
		final long startTime = System.nanoTime();

		// The event may have reached consensus while waiting in the queue.
		// Use consensus time if available. Otherwise, use an estimated time.
		Instant consensusTime = event.getConsensusTimestamp();
		if (consensusTime == null) {
			consensusTime = event.getEstimatedTime();
		}

		// Say that this event is not consensus, even if it is, to keep the contract that every
		// transaction is handled twice, once with isConsensus = false and once with isConsensus = true
		eventHandler.handleEventTransactions(event, consensusTime, false, stateCurrAndCons);

		stats.preConsensusHandleTime(startTime, System.nanoTime());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleConsensusRound(final ConsensusRound round) {
		synchronized (stateCurrAndCons) {
			for (final EventImpl event : round.getConsensusEvents()) {
				eventHandler.handleEventTransactions(event, event.getConsensusTimestamp(), true, stateCurrAndCons);
			}
		}
	}

	/**
	 * Return the current state of the app. It changes frequently, so this needs to be called frequently. This method
	 * also keeps track of which state was last returned, so that it can guarantee that that state will not be deleted.
	 *
	 * @return the current app state
	 */
	public SwirldState getCurrentSwirldState() {
		return stateCurrAndCons.getState().getSwirldState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public State getConsensusState() {
		return stateCurrAndCons.getState();
	}

	/**
	 * Performs any actions necessary to prepare for reconnect. This may include stopping threads, clearing queues,
	 * releasing references to state, etc.
	 *
	 * @throws InterruptedException
	 * 		if this thread is interrupted
	 */
	@Override
	public void prepareForReconnect() throws InterruptedException {
		// clear the transactions
		LOG.info(RECONNECT.getMarker(), "prepareForReconnect: clearing transactionPool");
		transactionPool.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void savedStateInFreezePeriod() {
		synchronized (stateCurrAndCons) {
			// set current DualState's lastFrozenTime to be current freezeTime
			stateCurrAndCons.getState().getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setState(final State state) {
		state.throwIfReleased("state must not be released");
		state.throwIfImmutable("state must be mutable");
		if (stateCurrAndCons != null) {
			stateCurrAndCons.release();
		}
		stateCurrAndCons = new StateInfo(state, null, false);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clearFreezeTimes() {
		synchronized (stateCurrAndCons) {
			stateCurrAndCons.getState().getPlatformDualState().setFreezeTime(null);
			stateCurrAndCons.getState().getPlatformDualState().setLastFrozenTimeToBeCurrentFreezeTime();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expandSignatures(final EventImpl event) {
		signatureExpander.expandSignatures(event.getTransactions(), getConsensusState());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInFreezePeriod(final Instant timestamp) {
		return SwirldStateManagerUtils.isInFreezePeriod(timestamp, getConsensusState());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public State getStateForSigning() {
		return SwirldStateManagerUtils.getStateForSigning(stateCurrAndCons, stats);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		return SwirldStateManagerUtils.shouldSyncAndCreate(getConsensusState());
	}

	/**
	 * {@inheritDoc}
	 */
	public EventTransactionPool getTransactionPool() {
		return transactionPool;
	}
}

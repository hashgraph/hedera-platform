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
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.stats.SwirldStateStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.swirlds.common.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EVENT_CONTENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;
import static com.swirlds.platform.event.EventUtils.toShortString;

/**
 * Handles transactions by passing them to a {@link com.swirlds.common.SwirldState#handleTransaction(long, boolean,
 * Instant, Instant, SwirldTransaction, SwirldDualState)}.
 */
public class TransactionHandler {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** The id of this node. */
	private final NodeId selfId;

	/** Stats relevant to SwirldState operations. */
	private final SwirldStateStats stats;

	/** Handles system transactions */
	private final SystemTransactionHandler systemTransactionHandler;

	public TransactionHandler(
			final NodeId selfId,
			final SystemTransactionHandler systemTransactionHandler,
			final SwirldStateStats stats) {
		this.selfId = selfId;
		this.systemTransactionHandler = systemTransactionHandler;
		this.stats = stats;
	}

	/**
	 * the consensus timestamp of a transaction is guaranteed to be at least this many nanoseconds
	 * later than that of the transaction immediately before it in consensus order,
	 * and to be a multiple of this (must be positive and a multiple of 10)
	 */
	public static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;

	/**
	 * Handles all the transactions in a given event. Equivalent to {@link #handleEventTransactions(EventImpl, Instant,
	 * boolean, StateInfo, Runnable)} with a no-op runnable.
	 *
	 * @param event
	 * 		the event to handle
	 * @param consTime
	 * 		the event's actual or estimated consensus time
	 * @param isConsensus
	 * 		if this event has reached consensus
	 * @param stateInfo
	 * 		the state to apply transaction to
	 */
	public void handleEventTransactions(final EventImpl event, final Instant consTime, final boolean isConsensus,
			final StateInfo stateInfo) {
		handleEventTransactions(event, consTime, isConsensus, stateInfo, null);
	}

	/**
	 * Handles all the transactions in a given event.
	 *
	 * @param event
	 * 		the event to handle
	 * @param consTime
	 * 		the event's actual or estimated consensus time
	 * @param isConsensus
	 * 		if this event has reached consensus
	 * @param stateInfo
	 * 		the state to apply transaction to
	 * @param postHandle
	 * 		a runnable to execute after handling each transaction
	 */
	public void handleEventTransactions(final EventImpl event, final Instant consTime, final boolean isConsensus,
			final StateInfo stateInfo, final Runnable postHandle) {
		if (event.isEmpty()) {
			return;
		}

		// the creator of the event containing this transaction
		final long creator = event.getCreatorId();

		// The claimed creation time of the event holding this transaction
		final Instant timeCreated = event.getTimeCreated();

		final boolean runPostHandle = postHandle != null;

		final Transaction[] transactions = event.getTransactions();
		for (int i = 0; i < transactions.length; i++) {
			final Instant transConsTime = consTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);

			// Synchronizing on each transaction is more performant than synchronizing on the entire event. It prevents
			// the consensus handler thread from waiting too long to acquire the lock to make a fast copy
			synchronized (stateInfo) {
				handleTransaction(event, isConsensus, creator, timeCreated, transConsTime, transactions[i],
						stateInfo);
			}

			if (runPostHandle) {
				postHandle.run();
			}

		}
	}

	/**
	 * <p>Handles a single transaction. {@code stateInfo} must not be modified while this method is executing.</p>
	 *
	 * @param event
	 * 		the event the transaction is in
	 * @param isConsensus
	 * 		if the transaction's event has reached consensus
	 * @param creator
	 * 		the creator of the transaction
	 * @param timeCreated
	 * 		the time the event was created as claimed by its creator
	 * @param transConsTime
	 * 		the transaction's actual or estimated consensus time
	 * @param trans
	 * 		the transaction
	 */
	public void handleTransaction(final EventImpl event, final boolean isConsensus, final long creator,
			final Instant timeCreated, final Instant transConsTime, final Transaction trans,
			final StateInfo stateInfo) {

		// system transactions are handled pre-consensus in SystemTransactionHandlerImpl, so don't handle them here
		// unless they have reached consensus
		if (trans.isSystem() && !isConsensus) {
			return;
		}

		// don't send a state any transactions after we promised not to.
		if (stateInfo.isFrozen()) {
			LOG.error("ERROR: handleTransaction was given a transaction for a frozen state");
			return;
		}

		// guard against bad apps crashing the browser
		try {
			if (trans.isSystem()) {
				systemTransactionHandler.handleSystemTransaction(
						creator,
						isConsensus,
						timeCreated,
						transConsTime,
						trans);
			} else {
				final SwirldTransaction swirldTransaction = (SwirldTransaction) trans;

				if (isConsensus) {
					validateSignatures(swirldTransaction);
				}

				final long startTime = System.nanoTime();

				stateInfo.getState().getSwirldState().handleTransaction(
						creator,
						isConsensus,
						timeCreated,
						transConsTime,
						swirldTransaction,
						stateInfo.getState().getSwirldDualState()
				);

				// clear sigs to free up memory, since we don't need them anymore
				if (isConsensus) {
					swirldTransaction.clearSignatures();
				}

				/* We only add these stats for transactions that have reached consensus. Use isConsensus to check the
				consensus status because these stats should only be recorded for events being handled as consensus
				events. Events in the pre-consensus queue could reach consensus before being handled pre-consensus. */
				if (event != null && isConsensus) {
					stats.consensusTransHandleTime((System.nanoTime() - startTime) * NANOSECONDS_TO_SECONDS);
					stats.consensusTransHandled();
					stats.consensusToHandleTime(
							event.getReachedConsTimestamp().until(Instant.now(),
									ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
				}
			}
		} catch (final InterruptedException ex) {
			handleInterruptedException();
		} catch (final Exception ex) {
			handleException(event, trans, stateInfo, ex);
		}
	}

	private void handleInterruptedException() {
		LOG.info(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
				"handleTransaction Interrupted [ nodeId = {} ]. " +
						"This should happen only during a reconnect",
				selfId.getId());
		Thread.currentThread().interrupt();
	}

	private void handleException(final EventImpl event, final Transaction trans, final StateInfo stateInfo,
			final Exception ex) {
		LOG.error(EXCEPTION.getMarker(),
				"error while calculating parameters to send {} or while calling it with event {}",
				((stateInfo == null || stateInfo.getState() == null)
						? "platform.handleTransaction"
						: "the app's SwirldState.handleTransaction"),
				toShortString(event), ex);

		LOG.error(EVENT_CONTENT.getMarker(),
				"error calculating parameters while calling it using a context \nwith event: {}\nwith trans: " +
						"{}",
				() -> event, trans::toString);
	}

	/**
	 * <p>Applies transactions to a state. These transactions have no event context and are applied with {@code
	 * consensus == false} and an estimated consensus time.</p>
	 *
	 * @param numTransSupplier
	 * 		a supplier for the number of transactions the {@code transSupplier} can provide
	 * @param transSupplier
	 * 		the supplier of transactions to apply
	 * @param stateInfo
	 * 		the state to apply the transactions to
	 */
	public void handleTransactions(final IntSupplier numTransSupplier,
			final Supplier<Transaction> transSupplier, final Supplier<Instant> consEstimateSupplier,
			final StateInfo stateInfo) {

		final int numTrans = numTransSupplier.getAsInt();

		if (numTrans <= 0) {
			return;
		}

		// the timestamp that we estimate the transactions will have after
		// being put into an event and having consensus reached on them
		final Instant baseTime = consEstimateSupplier.get();

		synchronized (stateInfo) {
			for (int i = 0; i < numTrans; i++) {
				// This call must acquire a lock
				final Transaction trans = transSupplier.get();
				if (trans == null) {
					// this shouldn't be necessary, but it's here just for safety
					break;
				}
				final Instant transConsTime = baseTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
				handleTransaction(null, false, selfId.getId(), Instant.now(), transConsTime, trans,
						stateInfo);
			}
		}
	}

	/**
	 * Validates any signatures present and waits if necessary.
	 *
	 * @param swirldTransaction
	 * 		the transaction whose signatures need to be validated
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	private static void validateSignatures(final SwirldTransaction swirldTransaction) throws InterruptedException,
			ExecutionException {

		// Validate any signatures present and wait if necessary
		for (final TransactionSignature sig : swirldTransaction.getSignatures()) {
			final Future<Void> future = sig.waitForFuture();

			// Block & Ignore the Void return
			future.get();
		}
	}
}

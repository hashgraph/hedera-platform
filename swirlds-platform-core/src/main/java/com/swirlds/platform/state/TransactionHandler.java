/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.common.system.transaction.Transaction;
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

import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EVENT_CONTENT;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;
import static com.swirlds.platform.event.EventUtils.toShortString;

/**
 * Handles transactions by passing them to a {@link SwirldState#handleTransaction(long, boolean,
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
	 * boolean, State, Runnable)} with a no-op runnable.
	 *
	 * @param event
	 * 		the event to handle
	 * @param consTime
	 * 		the event's actual or estimated consensus time
	 * @param isConsensus
	 * 		if this event has reached consensus
	 * @param state
	 * 		the state to apply transaction to
	 */
	public void handleEventTransactions(final EventImpl event, final Instant consTime, final boolean isConsensus,
			final State state) {
		handleEventTransactions(event, consTime, isConsensus, state, null);
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
	 * @param state
	 * 		the state to apply transaction to
	 * @param postHandle
	 * 		a runnable to execute after handling each transaction
	 */
	public void handleEventTransactions(final EventImpl event, final Instant consTime, final boolean isConsensus,
			final State state, final Runnable postHandle) {
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

			handleTransaction(event, isConsensus, creator, timeCreated, transConsTime, transactions[i], state);

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
			final State state) {

		// system transactions are handled pre-consensus in SystemTransactionHandlerImpl, so don't handle them here
		// unless they have reached consensus
		if (trans.isSystem() && !isConsensus) {
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

				state.getSwirldState().handleTransaction(
						creator,
						isConsensus,
						timeCreated,
						transConsTime,
						swirldTransaction,
						state.getSwirldDualState());

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
					// events being played back from the stream files during recovery do not have reachedConsTimestamp
					// set, since reachedConsTimestamp is not serialized and saved to the stream file.
					if (event.getReachedConsTimestamp() != null) {
						stats.consensusToHandleTime(
								event.getReachedConsTimestamp().until(Instant.now(),
										ChronoUnit.NANOS) * NANOSECONDS_TO_SECONDS);
					}
				}
			}
		} catch (final InterruptedException ex) {
			handleInterruptedException();
		} catch (final Exception ex) {
			handleException(event, trans, state, ex);
		}
	}

	private void handleInterruptedException() {
		LOG.info(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
				"handleTransaction Interrupted [ nodeId = {} ]. " +
						"This should happen only during a reconnect",
				selfId.getId());
		Thread.currentThread().interrupt();
	}

	private void handleException(final EventImpl event, final Transaction trans, final State state,
			final Exception ex) {
		LOG.error(EXCEPTION.getMarker(),
				"error while calculating parameters to send {} or while calling it with event {}",
				(state == null
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
	 * @param state
	 * 		the state to apply the transactions to
	 */
	public void handleTransactions(final IntSupplier numTransSupplier,
			final Supplier<Transaction> transSupplier, final Supplier<Instant> consEstimateSupplier,
			final State state) {

		final int numTrans = numTransSupplier.getAsInt();

		if (numTrans <= 0) {
			return;
		}

		// the timestamp that we estimate the transactions will have after
		// being put into an event and having consensus reached on them
		final Instant baseTime = consEstimateSupplier.get();

		for (int i = 0; i < numTrans; i++) {
			// This call must acquire a lock
			final Transaction trans = transSupplier.get();
			if (trans == null) {
				// this shouldn't be necessary, but it's here just for safety
				break;
			}
			final Instant transConsTime = baseTime.plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
			handleTransaction(null, false, selfId.getId(), Instant.now(), transConsTime, trans, state);
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

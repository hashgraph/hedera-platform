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
package com.swirlds.platform.eventhandling;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRule;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.platform.components.TransactionPool;
import com.swirlds.platform.components.TransactionSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.function.BooleanSupplier;

import static com.swirlds.common.system.transaction.TransactionType.SYS_TRANS_STATE_SIG;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.PASS;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;

/**
 * Store a list of transactions created by self, both system and non-system, for wrapping in the next
 * event to be created.
 */
public class EventTransactionPool implements TransactionPool, TransactionSupplier, TransThrottleSyncAndCreateRule {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/** list of transactions by self waiting to be put into an event */
	protected final LinkedList<ConsensusTransactionImpl> transEvent = new LinkedList<>();

	/** the number of user transactions in the transEvent list */
	protected volatile int numUserTransEvent = 0;

	/** the number of signature system transactions in the transEvent list */
	protected volatile int numSignatureTransEvent = 0;

	/** Indicates if the system is currently in a freeze. */
	private final BooleanSupplier inFreeze;

	protected final SettingsProvider settings;

	// Used for creating spy objects for unit tests
	public EventTransactionPool() {
		settings = null;
		inFreeze = null;
	}

	/**
	 * Creates a new transaction pool for transactions waiting to be put in an event.
	 *
	 * @param settings
	 * 		settings to use
	 * @param inFreeze
	 * 		Indicates if the system is currently in a freeze
	 */
	public EventTransactionPool(final SettingsProvider settings, final BooleanSupplier inFreeze) {
		this.settings = settings;
		this.inFreeze = inFreeze;
	}

	/**
	 * Removes as many transactions from the list waiting to be in an event that can fit (FIFO ordering), and returns
	 * them as an array, along with a boolean indicating if the array of transactions returned contains a freeze state
	 * signature transaction.
	 */
	@Override
	public synchronized ConsensusTransactionImpl[] getTransactions() {
		// Early return due to no transactions waiting
		if (transEvent.isEmpty()) {
			return new ConsensusTransactionImpl[0];
		}

		final LinkedList<ConsensusTransactionImpl> selectedTrans = new LinkedList<>();
		int currEventSize = 0;

		while (currEventSize < settings.getMaxTransactionBytesPerEvent() && !transEvent.isEmpty()) {
			final ConsensusTransaction trans = transEvent.peek();

			if (trans != null) {
				// This event already contains transactions
				// The next transaction is larger than the remaining space in the event
				if (trans.getSerializedLength() > (settings.getMaxTransactionBytesPerEvent() - currEventSize)) {
					break;
				}

				currEventSize += trans.getSerializedLength();
				selectedTrans.offer(transEvent.poll());
				if (!trans.isSystem()) {
					numUserTransEvent--;
				} else {
					if (trans.getTransactionType() == SYS_TRANS_STATE_SIG) {
						numSignatureTransEvent--;
					}
				}
			}
		}

		return selectedTrans.toArray(new ConsensusTransactionImpl[0]);
	}

	/**
	 * @return the number of transactions waiting to be put in an event, both user and state signature system
	 * 		transactions
	 */
	public int numTransForEvent() {
		return numUserTransEvent + numSignatureTransEvent;
	}

	/**
	 * @return the number of signature transactions waiting to be put in an event.
	 */
	public int numSignatureTransEvent() {
		return numSignatureTransEvent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventCreationRuleResponse shouldCreateEvent() {
		if (numSignatureTransEvent() > 0 && inFreeze.getAsBoolean()) {
			return EventCreationRuleResponse.CREATE;
		} else {
			return EventCreationRuleResponse.PASS;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public TransThrottleSyncAndCreateRuleResponse shouldSyncAndCreate() {
		// if we have transactions waiting to be put into an event, initiate a sync
		if (numTransForEvent() > 0) {
			return SYNC_AND_CREATE;
		} else {
			return PASS;
		}
	}

	/**
	 * Add the given transaction to the list. If it is full, it does nothing and returns false immediately.
	 *
	 * @param trans
	 * 		The transaction. It must have been created by self.
	 * @return true if successful
	 */
	public synchronized boolean submitTransaction(final ConsensusTransactionImpl trans) {
		// Always submit system transactions
		if (!trans.isSystem() && transEvent.size() > settings.getThrottleTransactionQueueSize()) {
			return false;
		}

		// this will be true, unless a bad error has occurred
		return offerToEventQueue(trans);
	}

	/**
	 * This method should only ever be called after ensuring the transaction is either a system transaction or
	 * that the transEvent size is less than {@link SettingsProvider#getThrottleTransactionQueueSize}.
	 *
	 * @param trans
	 * 		the transaction to add
	 * @return true if the transaction was added to the queue
	 */
	protected synchronized boolean offerToEventQueue(final ConsensusTransactionImpl trans) {
		final boolean ans = transEvent.offer(trans);
		if (ans) {
			if (!trans.isSystem()) {
				numUserTransEvent++;
			} else {
				if (trans.getTransactionType() == SYS_TRANS_STATE_SIG) {
					numSignatureTransEvent++;
				}
			}
		} else {
			LOG.error(EXCEPTION.getMarker(), "transEvent queue was shorter " +
					"than Settings.throttleTransactionQueueSize, yet offer returned false");
		}
		return ans;
	}

	/**
	 * get the number of transactions in the transEvent queue
	 *
	 * @return the number of transactions
	 */
	public synchronized int getEventSize() {
		return transEvent.size();
	}

	/** return a single string giving the number of transactions in transEvent */
	public synchronized String status() {
		return "transEvent size =" + transEvent.size();
	}

	/**
	 * Clear all the transactions
	 */
	public synchronized void clear() {
		transEvent.clear();
		numUserTransEvent = 0;
		numSignatureTransEvent = 0;
	}
}

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

package com.swirlds.platform.components;

import com.swirlds.common.NodeId;
import com.swirlds.common.Transaction;
import com.swirlds.common.transaction.internal.StateSignatureTransaction;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;

/** Handles all system transactions */
public class SystemTransactionHandlerImpl implements SystemTransactionHandler, PreConsensusEventObserver {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * The ID of this node
	 */
	private final NodeId selfId;

	/**
	 * An implementor of {@code StateSignatureRecorder}
	 */
	private final StateSignatureRecorder recorder;

	/**
	 * Constructor
	 *
	 * @param selfId
	 * 		the ID of this node
	 * @param recorder
	 * 		object responsible for recording the state signature
	 */
	public SystemTransactionHandlerImpl(final NodeId selfId, final StateSignatureRecorder recorder) {
		this.selfId = selfId;
		this.recorder = recorder;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleSystemTransaction(final long creator, final boolean isConsensus,
			final Instant timeCreated, final Instant timestamp, final Transaction trans) {
		try {
			switch (trans.getTransactionType()) {
				case SYS_TRANS_STATE_SIG: // a signature on a signed state
				case SYS_TRANS_STATE_SIG_FREEZE: // the same thing on the receiving side
					// self-signature was recorded when it was created, so only record other-sigs here
					if (!selfId.equalsMain(creator)) {
						final StateSignatureTransaction signatureTransaction = (StateSignatureTransaction) trans;
						final long lastRoundReceived = signatureTransaction.getLastRoundReceived();
						final byte[] sig = signatureTransaction.getStateSignature();
						LOG.debug(STATE_SIG_DIST.getMarker(),
								"platform {} got sig from {} for round {} at handleSystemTransaction",
								selfId, creator, lastRoundReceived);
						recorder.recordStateSig(lastRoundReceived,
								creator, null, sig);
					}
					break;
				case SYS_TRANS_PING_MICROSECONDS: // latency between members
				case SYS_TRANS_BITS_PER_SECOND: // throughput between members
					break;

				default:
					LOG.error(EXCEPTION.getMarker(),
							"Unknown system transaction type {}",
							trans.getTransactionType());
					break;
			}
		} catch (final RuntimeException e) {
			LOG.error(EXCEPTION.getMarker(),
					"Error while handling transaction: kind {} id {} isConsensus {} transaction {} error",
					trans.getTransactionType(),
					creator, isConsensus, trans, e);

		}
	}

	/**
	 * handles system transactions in this event
	 *
	 * @param event
	 * 		the event to be added to the hashgraph
	 */
	@Override
	public void preConsensusEvent(final EventImpl event) {
		final Transaction[] trans = event.getTransactions();
		final int numTrans = (trans == null ? 0 : trans.length);
		for (int i = 0; i < numTrans; i++) {
			if (trans[i].isSystem()) {
				// All system transactions are handled twice, once with consensus false, and once with true.
				// This is the first time a system transaction is handled while its consensus is not yet
				// known. The second time it will be handled is in TransactionHandler by the thread-cons thread (lives
				// in ConsensusEventHandler)
				handleSystemTransaction(event.getCreatorId(), false,
						event.getTimeCreated(),
						event.getTimeCreated().plusNanos(i), trans[i]);
			}
		}
	}
}

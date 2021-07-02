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
public class SystemTransactionHandler implements PreConsensusEventObserver {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

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
	public SystemTransactionHandler(NodeId selfId, StateSignatureRecorder recorder) {
		this.selfId = selfId;
		this.recorder = recorder;
	}

	/**
	 * All system transactions are handled by calling this method. handleSystemTransaction is the
	 * equivalent of SwirldMain.handleTransaction, except that the "app" is the Platform itself.
	 * <p>
	 * Every system transaction is sent here twice, first with consensus being false, and again with
	 * consensus being true. In other words, the platform will act like an app that implements SwirldState2
	 * rather than an app implementing SwirldState.
	 * <p>
	 * This method is called by the doCons thread in EventFlow, and by this::preConsensusEvent. But it could have
	 * deadlock problems if it tried to get a lock on Platform or Hashgraph while, for example, one of the syncing
	 * threads is  holding those locks and waiting for the forCons queue to not be full. So if this method is changed,
	 * it should avoid that.
	 *
	 * @param creator
	 * 		the ID number of the member who created this transaction
	 * @param isConsensus
	 * 		is this transaction's timeCreated and position in history part of the consensus?
	 * @param timeCreated
	 * 		the time when this transaction was first created and sent to the network, as claimed by
	 * 		the member that created it (which might be dishonest or mistaken)
	 * @param timestamp
	 * 		the consensus timestamp for when this transaction happened (or an estimate of it, if it
	 * 		hasn't reached consensus yet)
	 * @param trans
	 * 		the transaction to handle, encoded any way the swirld app author chooses
	 */
	public void handleSystemTransaction(final long creator, final boolean isConsensus,
			final Instant timeCreated, final Instant timestamp, final Transaction trans) {
		try {
			switch (trans.getTransactionType()) {
				case SYS_TRANS_STATE_SIG: // a signature on a signed state
				case SYS_TRANS_STATE_SIG_FREEZE: // the same thing on the receiving side
					// self-signature was recorded when it was created, so only record other-sigs here
					if (!selfId.equalsMain(creator)) {
						StateSignatureTransaction signatureTransaction = (StateSignatureTransaction) trans;
						final long lastRoundReceived = signatureTransaction.getLastRoundReceived();
						final byte[] sig = signatureTransaction.getStateSignature();
						log.debug(STATE_SIG_DIST.getMarker(),
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
					log.error(EXCEPTION.getMarker(),
							"Unknown system transaction type {}",
							trans.getTransactionType());
					break;
			}
		} catch (RuntimeException e) {
			log.error(EXCEPTION.getMarker(),
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
		int numTrans = (trans == null ? 0 : trans.length);
		for (int i = 0; i < numTrans; i++) {
			if (trans[i].isSystem()) {
				// All system transactions are handled twice, once with consensus false, and once with true.
				// This is the first time a system transaction is handled while its consensus is not yet
				// known. The second time it will be handled is in EventFlow.doCons()
				handleSystemTransaction(event.getCreatorId(), false,
						event.getTimeCreated(),
						event.getTimeCreated().plusNanos(i), trans[i]);
			}
		}
	}
}

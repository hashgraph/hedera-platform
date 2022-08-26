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

package com.swirlds.platform.components;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;

/** Handles all system transactions */
public class SystemTransactionHandlerImpl implements SystemTransactionHandler {
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
			final Instant timeCreated, final SystemTransaction trans) {
		try {
			switch (trans.getTransactionType()) {
				case SYS_TRANS_STATE_SIG: // a signature on a signed state
					// self-signature was recorded when it was created, so only record other-sigs here
					if (!selfId.equalsMain(creator)) {
						final StateSignatureTransaction signatureTransaction = (StateSignatureTransaction) trans;
						final long lastRoundReceived = signatureTransaction.getLastRoundReceived();
						final byte[] sig = signatureTransaction.getStateSignature();
						LOG.debug(STATE_SIG_DIST.getMarker(),
								"platform {} got sig from {} for round {} at handleSystemTransaction",
								selfId, creator, lastRoundReceived);
						recorder.recordStateSig(lastRoundReceived, creator, null, sig);
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
}

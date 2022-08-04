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

import com.swirlds.common.system.transaction.Transaction;

import java.time.Instant;

/**
 * Handles system transactions.
 */
public interface SystemTransactionHandler {

	/**
	 * All system transactions are handled by calling this method. handleSystemTransaction is the
	 * equivalent of SwirldMain.handleTransaction, except that the "app" is the Platform itself.
	 * <p>
	 * Every system transaction is sent here twice, first with consensus being false, and again with
	 * consensus being true. In other words, the platform will act like an app that implements SwirldState2
	 * rather than an app implementing SwirldState.
	 * <p>
	 * This method is called by the thread-cons thread in ConsensusRoundHandler, and by this::preConsensusEvent. But it
	 * could have deadlock problems if it tried to get a lock on Platform or Hashgraph while, for example, one of the
	 * syncing threads is  holding those locks and waiting for the forCons/threadCons queue to not be full. So if this
	 * method is changed, it should avoid that.
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
	void handleSystemTransaction(final long creator, final boolean isConsensus,
			final Instant timeCreated, final Instant timestamp, final Transaction trans);
}

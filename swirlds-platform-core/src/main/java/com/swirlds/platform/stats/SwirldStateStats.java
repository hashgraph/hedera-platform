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

package com.swirlds.platform.stats;

import com.swirlds.common.SwirldState;

public interface SwirldStateStats {

	/**
	 * Records the amount of time to handle a consensus transaction in {@link SwirldState}.
	 *
	 * @param seconds
	 */
	void consensusTransHandleTime(double seconds);

	/**
	 * Records the amount of time between a transaction reaching consensus and being handled in {@link SwirldState}.
	 *
	 * @param seconds
	 */
	void consensusToHandleTime(double seconds);

	/**
	 * Records the fact that a consensus transaction was handled by {@link SwirldState}.
	 */
	void consensusTransHandled();

	/**
	 * Records the time it takes {@link SwirldState#copy()} to finish (in microseconds)
	 *
	 * @param micros
	 */
	void stateCopyMicros(final double micros);

	/**
	 * Records the time spent performing a shuffle in {@link com.swirlds.platform.state.SwirldStateManagerSingle}  (in
	 * microseconds).
	 *
	 * @param micros
	 */
	void shuffleMicros(final double micros);

	/**
	 * The average difference in creation time and consensus time for self events.
	 *
	 * @return
	 */
	double getAvgSelfCreatedTimestamp();

	/**
	 * The average difference in creation time and consensus time for other events.
	 *
	 * @return
	 */
	double getAvgOtherReceivedTimestamp();

	/**
	 * The start and end time of {@link SwirldState#noMoreTransactions()} execution
	 */
	void noMoreTransactionsMicros(final long start, final long end);

	void stateCopyAdmit(final long start, final long end);

	void preConsensusHandleTime(long start, final long end);
}

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

import com.swirlds.platform.EventImpl;

public interface ConsensusStats extends DefaultStats {
	/**
	 * Update a statistics accumulator when an gossiped event has been added to the hashgraph.
	 * @param event
	 * 		an event
	 */
	void addedEvent(EventImpl event);

	/**
	 * Update a statistics accumulator when a number coin rounds have occurred.
	 *
	 * @param numCoinRounds
	 * 		a number of coin rounds
	 */
	void coinRounds(long numCoinRounds);

	/**
	 * Update a statistics accumulator to receive the time when a gossiped event
	 * is the last famous witness in its round.
	 *
	 * @param event
	 * 		an event
	 */
	void lastFamousInRound(EventImpl event);

	// this might not need to be a separate method
	// we could just update the stats in consensusReached(EventImpl event) when event.lastInRoundReceived()==true
	/**
	 * Update a statistics accumulator a round has reached consensus.
	 */
	void consensusReachedOnRound();

	/**
	 * Update a statistics accumulator when an event reaches consensus
	 *
	 * @param event
	 * 		an event
	 */
	void consensusReached(EventImpl event);

	/**
	 * Update a statistics accumulator with dot product time. This is used
	 * in the consensus impl to track performance of strong seen ancestor searches
	 * in the parent round of a given event.
	 *
	 * @param nanoTime
	 * 		a time interval, in nanoseconds
	 */
	void dotProductTime(long nanoTime);
}

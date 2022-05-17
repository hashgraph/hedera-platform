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

import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;

/**
 * Provides access to statistics relevant to {@link ConsensusRoundHandler}
 */
public interface ConsensusHandlingStats {

	/**
	 * @return the cycle timing stat that keeps track of how much time is spent in various parts of {@link
	 *        ConsensusRoundHandler#consensusRound(ConsensusRound)}
	 */
	CycleTimingStat getConsCycleStat();

	/**
	 * @return the cycle timing stat that keeps track of how much time is spent creating a new signed state in {@link
	 *        ConsensusRoundHandler#consensusRound(ConsensusRound)}
	 */
	CycleTimingStat getNewSignedStateCycleStat();

	/**
	 * Records the number of events in a round.
	 *
	 * @param numEvents
	 * 		the number of events in the round
	 */
	void recordEventsPerRound(int numEvents);
}

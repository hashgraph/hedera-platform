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

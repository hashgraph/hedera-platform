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

package com.swirlds.platform.consensus;

import com.swirlds.common.system.events.Event;

public interface GraphGenerations {
	long FIRST_GENERATION = 0;

	/**
	 * @return The minimum judge generation number from the most recent fame-decided round, if there is one.
	 * 		Else this returns {@link #FIRST_GENERATION}.
	 */
	long getMaxRoundGeneration();

	/**
	 * @return The minimum generation of all the judges that are not ancient. If no judges are ancient, returns
	 *        {@link #FIRST_GENERATION}.
	 */
	long getMinGenerationNonAncient();

	/**
	 * @return The minimum judge generation number from the oldest non-expired round, if we have expired any rounds.
	 * 		Else this returns {@link #FIRST_GENERATION}.
	 */
	long getMinRoundGeneration();

	/**
	 * Checks if we have any ancient rounds yet. For a short period after genesis, there will be no ancient rounds.
	 * After that, this will always return true.
	 *
	 * @return true if there are any ancient events, false otherwise
	 */
	default boolean areAnyEventsAncient() {
		return getMinGenerationNonAncient() > FIRST_GENERATION;
	}

	/**
	 * Checks if the supplied event is ancient or not. An event is ancient if its generation is smaller than the round
	 * generation of the oldest non-ancient round.
	 *
	 * @param event
	 * 		the event to check
	 * @return true if its ancient, false otherwise
	 */
	default boolean isAncient(final Event event) {
		return event.getGeneration() < getMinGenerationNonAncient();
	}
}

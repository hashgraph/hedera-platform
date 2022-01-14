/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.consensus;

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
}

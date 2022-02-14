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

public interface RoundNumberProvider {
	/** the number used to represent that a round has not been defined */
	long ROUND_UNDEFINED = -1;

	/**
	 * return the round number below which the fame of all witnesses has been decided for all earlier rounds.
	 *
	 * @return the round number
	 */
	long getFameDecidedBelow();

	/**
	 * @return the latest round for which fame has been decided
	 */
	default long getLastRoundDecided() {
		return getFameDecidedBelow() - 1;
	}

	/**
	 * Return the max round number for which we have an event. If there are none yet, return {@link #ROUND_UNDEFINED}.
	 *
	 * @return the max round number, or {@link #ROUND_UNDEFINED} if none.
	 */
	long getMaxRound();

	/**
	 * Return the minimum round number for which we have an event. If there are none yet, return {@link
	 * #ROUND_UNDEFINED}.
	 *
	 * @return the minimum round number, or {@link #ROUND_UNDEFINED} if none.
	 */
	long getMinRound();

	/**
	 * Return the highest round number that has been deleted (or at least will be deleted soon).
	 *
	 * @return the round number that will be deleted (along with all earlier rounds)
	 */
	default long getDeleteRound() {
		return getMinRound() - 1;
	}
}

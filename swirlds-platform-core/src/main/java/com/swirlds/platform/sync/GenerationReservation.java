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

package com.swirlds.platform.sync;

import com.swirlds.common.AutoCloseableNonThrowing;

/**
 * Represents zero or more reservations for a generation. It is used to determine when it is safe to expire events in
 * a given generation. Reservations are made by gossip threads inside {@link ShadowGraph}. Generations that
 * have at least one reservation may not have any of its events expired. Implementations must decrement the number of
 * reservations on closing and must be safe for multiple threads to use simultaneously.
 */
public interface GenerationReservation extends AutoCloseableNonThrowing {

	/**
	 * Returns the generation this instance tracks reservations for. The returned value is always zero or greater.
	 *
	 * @return the generation number
	 */
	long getGeneration();

	/**
	 * Returns the number of reservations for this generation at the time of invocation.
	 *
	 * @return number of reservations
	 */
	int getNumReservations();
}

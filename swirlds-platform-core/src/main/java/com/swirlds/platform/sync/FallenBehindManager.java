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

import com.swirlds.common.system.NodeId;

import java.util.List;

public interface FallenBehindManager {
	/**
	 * Notify the fallen behind manager that a node has reported that they don't have events we need. This means we have
	 * probably fallen behind and will need to reconnect
	 *
	 * @param id
	 * 		the id of the node who says we have fallen behind
	 */
	void reportFallenBehind(NodeId id);

	/**
	 * We have determined that we have not fallen behind, or we have reconnected, so reset everything to the initial
	 * state
	 */
	void resetFallenBehind();

	/**
	 * Have enough nodes reported that they don't have events we need, and that we have fallen behind?
	 *
	 * @return true if we have fallen behind, false otherwise
	 */
	boolean hasFallenBehind();

	/**
	 * Get a list of neighbors to call if we need to do a reconnect
	 *
	 * @return a list of neighbor IDs
	 */
	List<Long> getNeighborsForReconnect();
}

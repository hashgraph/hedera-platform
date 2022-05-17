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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.merkle.MerkleInternal;

/**
 * Methods used by the learner to initialize nodes within a view.
 *
 * @param <T>
 * 		the type of object used to represent merkle nodes in the view
 */
public interface LearnerInitializer<T> {

	/**
	 * Mark a node for later initialization. If a node type is known not to require
	 * initialization, no action is required.
	 *
	 * @param node
	 * 		the node to later initialize
	 */
	void markForInitialization(T node);

	/**
	 * <p>
	 * Initialize each internal node that was reconstructed via this algorithm by calling
	 * {@link MerkleInternal#initialize()}. No action is required for node types that are known
	 * to not require initialization.
	 * </p>
	 *
	 * <p>
	 * Initialization is required to initialize children before their parents.
	 * </p>
	 *
	 * <p>
	 * This method is called exactly once when the synchronization algorithm has completely transferred the entire tree.
	 * </p>
	 *
	 * <p>
	 * It is ok if this method is also used to initialize other parts or components of the tree.
	 * </p>
	 */
	void initialize();

}

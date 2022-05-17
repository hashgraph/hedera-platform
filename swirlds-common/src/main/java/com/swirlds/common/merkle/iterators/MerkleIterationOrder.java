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

package com.swirlds.common.merkle.iterators;

/**
 * Defines different iteration orders for merkle trees.
 */
public enum MerkleIterationOrder {
	/**
	 * A depth first traversal where parents are visited after their children (i.e. standard depth first).
	 * This is the default iteration order for a {@link MerkleIterator}.
	 */
	POST_ORDERED_DEPTH_FIRST,
	/**
	 * Similar to {@link #POST_ORDERED_DEPTH_FIRST}, but with order between sibling subtrees randomized.
	 */
	POST_ORDERED_DEPTH_FIRST_RANDOM,
	/**
	 * A depth first traversal where parents are visited before their children.
	 */
	PRE_ORDERED_DEPTH_FIRST,
	/**
	 * A breadth first traversal.
	 */
	BREADTH_FIRST,
}

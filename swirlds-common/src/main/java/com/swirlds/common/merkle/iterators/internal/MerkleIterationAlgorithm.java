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

package com.swirlds.common.merkle.iterators.internal;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.function.ObjIntConsumer;

/**
 * Defines algorithm specific behavior for a merkle iterator.
 */
public interface MerkleIterationAlgorithm {

	/**
	 * Push a node into the stack/queue.
	 */
	void push(MerkleNode node);

	/**
	 * Remove and return the next item in the stack/queue.
	 */
	MerkleNode pop();

	/**
	 * Return the next item in the stack/queue but do not remove it.
	 */
	MerkleNode peek();

	/**
	 * Get the number of elements in the stack/queue.
	 */
	int size();

	/**
	 * Call pushNode on all of a merkle node's children.
	 *
	 * @param parent
	 * 		the parent with children to push
	 * @param pushNode
	 * 		a method to be used to push children. First argument is the parent, second argument is the child index.
	 */
	void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode);
}

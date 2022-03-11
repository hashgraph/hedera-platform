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

package com.swirlds.common.merkle.iterators.internal;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#BREADTH_FIRST BREADTH_FIRST}.
 */
public class BreadthFirstAlgorithm implements MerkleIterationAlgorithm {

	private final Queue<MerkleNode> queue = new LinkedList<>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void push(final MerkleNode node) {
		queue.add(node);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleNode pop() {
		return queue.remove();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleNode peek() {
		return queue.peek();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return queue.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
		for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
			pushNode.accept(parent, childIndex);
		}
	}
}

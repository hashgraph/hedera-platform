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

import java.util.Deque;
import java.util.LinkedList;
import java.util.Stack;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#POST_ORDERED_DEPTH_FIRST POST_ORDER_DEPTH_FIRST}.
 */
public class PostOrderedDepthFirstAlgorithm implements MerkleIterationAlgorithm {

	/**
	 * A {@link Deque} is used instead of a {@link Stack} since {@link Stack} is an old school
	 * container object that contains unnecessary synchronization.
	 */
	private final Deque<MerkleNode> stack = new LinkedList<>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void push(final MerkleNode node) {
		stack.push(node);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleNode pop() {
		return stack.pop();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleNode peek() {
		return stack.peek();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return stack.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
		for (int childIndex = parent.getNumberOfChildren() - 1; childIndex >= 0; childIndex--) {
			pushNode.accept(parent, childIndex);
		}
	}
}

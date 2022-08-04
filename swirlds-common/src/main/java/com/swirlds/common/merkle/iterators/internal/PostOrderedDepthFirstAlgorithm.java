/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

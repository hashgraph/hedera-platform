/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.merkle.iterators;

import com.swirlds.common.merkle.MerkleNode;

import java.util.Stack;

/**
 * Iterates over a merkle tree in a post ordered depth first traversal.
 * @param <T> The type of node over which this iterator walks. Usually MerkleNode is the correct choice for this
 *           unless there is an implementation specific method required by one of the overridable methods in this
 *           iterator.
 * @param <R> The type of the node returned by this iterator. Nodes of type T are cast into type R before being
 *           returned by next().
 */
public class MerkleDepthFirstIterator<T extends MerkleNode, R extends T> extends MerkleIterator<T, R> {

	private Stack<T> stack;

	public MerkleDepthFirstIterator(T root) {
		super(root);
	}

	@Override
	protected void init() {
		stack = new Stack<>();
	}

	@Override
	protected void push(T node) {
		stack.push(node);
	}

	@Override
	protected T pop() {
		return stack.pop();
	}

	@Override
	protected T peek() {
		return stack.peek();
	}

	@Override
	protected int size() {
		return stack.size();
	}

	@Override
	protected boolean reverseChildren() {
		return true;
	}


}

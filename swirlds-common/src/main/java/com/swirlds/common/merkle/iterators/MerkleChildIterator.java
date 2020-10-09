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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;

/**
 * This iterator walks over the direct children of a MerkleNode from left to right.
 */
public class MerkleChildIterator<R extends MerkleNode> implements Iterator<R> {

	private MerkleInternal root;
	private int nextChild;

	public MerkleChildIterator(MerkleNode root) {
		if (root != null && !root.isLeaf()) {
			this.root = (MerkleInternal) root;
			this.nextChild = 0;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		return root != null && nextChild < root.getNumberOfChildren();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public R next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		MerkleNode child = root.getChild(nextChild);
		nextChild++;
		return (R) child;
	}
}

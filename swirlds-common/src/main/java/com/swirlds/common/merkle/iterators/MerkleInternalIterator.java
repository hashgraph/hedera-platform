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

/**
 * This iterator walks over the tree in a depth first post ordered traversal. It only returns internal nodes.
 */
public class MerkleInternalIterator<R extends MerkleInternal> extends MerkleDepthFirstIterator<MerkleNode, R> {

	public MerkleInternalIterator(final MerkleNode root) {
		super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean shouldNodeBeReturned(final MerkleNode node) {
		return node != null && !node.isLeaf();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean shouldNodeBeVisited(final MerkleNode node) {
		return node != null && !node.isLeaf();
	}
}

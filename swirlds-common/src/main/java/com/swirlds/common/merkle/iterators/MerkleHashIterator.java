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

package com.swirlds.common.merkle.iterators;

import com.swirlds.common.merkle.MerkleNode;

/**
 * Iterates over all nodes in a merkle tree that need to be hashed.
 * Nodes are visited in a post-ordered depth first search.
 */
public class MerkleHashIterator extends MerkleDepthFirstIterator<MerkleNode, MerkleNode> {

	public MerkleHashIterator(MerkleNode root) {
		super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldNodeBeVisited(final MerkleNode node) {
		if (node.isSelfHashing()) {
			if (node.getHash() == null) {
				throw new IllegalStateException("A self hashing node " + node + " returned a null hash");
			}
			return false;
		}

		return node.getHash() == null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldNodeBeReturned(MerkleNode node) {
		if (node == null) {
			return false;
		}

		if (node.isSelfHashing()) {
			if (node.getHash() == null) {
				throw new IllegalStateException("A self hashing node " + node + " returned a null hash");
			}
			return false;
		}

		return node.getHash() == null;
	}
}

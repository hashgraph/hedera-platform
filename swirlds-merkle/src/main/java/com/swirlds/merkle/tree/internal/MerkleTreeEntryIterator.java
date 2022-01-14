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

package com.swirlds.merkle.tree.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;

/**
 * This iterator walks over the entries in a {@link MerkleBinaryTree}.
 *
 * @param <T>
 * 		the type of the entry
 */
public class MerkleTreeEntryIterator<T extends MerkleNode>
		extends MerkleDepthFirstIterator<MerkleNode, T> {

	/**
	 * Create a new iterator
	 *
	 * @param root
	 * 		the root of a {@link MerkleBinaryTree}
	 */
	public MerkleTreeEntryIterator(final MerkleTreeInternalNode root) {
		super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldChildBeConsidered(final MerkleNode parent, final MerkleNode child) {
		return parent.getClassId() == MerkleTreeInternalNode.CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldNodeBeReturned(final MerkleNode node) {
		return node != null && node.getClassId() != MerkleTreeInternalNode.CLASS_ID;
	}
}

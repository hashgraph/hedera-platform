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

package com.swirlds.common.merkle.io.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleBreadthFirstIterator;

import static com.swirlds.common.merkle.io.SerializationStrategy.DEFAULT_MERKLE_INTERNAL;

/**
 * An iterator used by the serialization algorithm to walk over the tree.
 */
public class MerkleSerializationIterator extends MerkleBreadthFirstIterator<MerkleNode, MerkleNode> {

	/**
	 * Create a new iterator.
	 *
	 * @param root
	 * 		the root of the tree
	 */
	public MerkleSerializationIterator(final MerkleNode root) {
		super(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean shouldChildBeConsidered(final MerkleNode parent, final MerkleNode child) {
		// don't iterate over children of nodes that are don't use standard serialization
		return parent.supportedSerialization(parent.getVersion()).contains(DEFAULT_MERKLE_INTERNAL);
	}
}

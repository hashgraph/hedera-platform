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

package com.swirlds.common.merkle.copy;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;

import static com.swirlds.common.merkle.io.SerializationStrategy.EXTERNAL_SELF_SERIALIZATION;
import static com.swirlds.common.merkle.io.SerializationStrategy.SELF_SERIALIZATION;

// Note: the implementation of this iteration strategy can be drastically simplified when the branch
// 03673-3538-merkle-map-conformity merges. That branch improves merkle iterators and makes it much
// easier to configure iterator behavior without subclassing.

/**
 * A special iterator for walking a tree (or subtree) that requires initialization.
 */
public class MerkleInitializationIterator extends MerkleDepthFirstIterator<MerkleNode, MerkleInternal> {

	private final InitializationType initializationType;

	/**
	 * Create an iterator that visits all nodes requiring initialization.
	 *
	 * @param root
	 * 		the root of the tree (or subtree)
	 * @param initializationType
	 * 		the operation that makes initialization necessary
	 */
	public MerkleInitializationIterator(
			final MerkleNode root,
			final InitializationType initializationType) {
		this.initializationType = initializationType;
		setup(root);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean shouldNodeBeVisited(final MerkleNode node) {
		if (node == null || node.isLeaf()) {
			return false;
		}

		switch (initializationType) {
			case DESERIALIZATION:
				return !node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION);
			case EXTERNAL_DESERIALIZATION:
				return !node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION) &&
						!node.supportedSerialization(node.getVersion()).contains(EXTERNAL_SELF_SERIALIZATION);
			case RECONNECT:
				// this needs to be updated once self reconnecting becomes a thing
				return true;
			case COPY:
				return true;
			default:
				throw new IllegalStateException("unsupported initialization type " + initializationType);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean shouldNodeBeReturned(final MerkleNode node) {
		return shouldNodeBeVisited(node);
	}
}

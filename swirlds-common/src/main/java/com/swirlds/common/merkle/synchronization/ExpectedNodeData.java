/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

/**
 * This object is used to track a node for which the learning synchronizer is expecting data.
 */
public class ExpectedNodeData {

	/**
	 * The hash that this node is expected to have.
	 */
	private final Hash hash;

	/**
	 * The node that will be the parent of this node.
	 */
	private final MerkleInternal parent;

	/**
	 * This node's eventual position within its parent.
	 */
	private final int positionInParent;

	/**
	 * The node that was originally in this position within the tree.
	 */
	private final MerkleNode originalNode;

	/**
	 * Create a record for a node for which we are expecting data.
	 *
	 * @param hash
	 * 		the expected hash of the node
	 * @param parent
	 * 		the eventual parent of the node
	 * @param positionInParent
	 * 		the eventual position of the node within its parent
	 * @param originalNode
	 * 		the node that was originally in this location in the tree
	 */
	public ExpectedNodeData(
			final Hash hash,
			final MerkleInternal parent,
			final int positionInParent,
			final MerkleNode originalNode) {
		this.hash = hash;
		this.parent = parent;
		this.positionInParent = positionInParent;
		this.originalNode = originalNode;
	}

	/**
	 * Get the expected hash of the node.
	 */
	public Hash getHash() {
		return hash;
	}

	/**
	 * Get the eventual parent of the node.
	 */
	public MerkleInternal getParent() {
		return parent;
	}

	/**
	 * Get the eventual position of the node within its parent.
	 */
	public int getPositionInParent() {
		return positionInParent;
	}

	/**
	 * Get the original node in this position.
	 */
	public MerkleNode getOriginalNode() {
		return originalNode;
	}

	/**
	 * For debugging purposes
	 */
	@Override
	public String toString() {
		return "(hash: " + hash + ", parent: " + parent + ", position: " +
				positionInParent + ", original: " + originalNode + ")";
	}
}

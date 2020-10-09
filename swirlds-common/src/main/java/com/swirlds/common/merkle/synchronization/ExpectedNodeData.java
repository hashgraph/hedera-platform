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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

/**
 * This object is used to track a node for which the learning synchronizer is expecting data.
 */
public class ExpectedNodeData {

	private Hash hash;
	private MerkleInternal parent;
	private int positionInParent;
	private MerkleNode originalNode;

	public ExpectedNodeData(Hash hash, MerkleInternal parent, int positionInParent, MerkleNode originalNode) {
		this.hash = hash;
		this.parent = parent;
		this.positionInParent = positionInParent;
		this.originalNode = originalNode;
	}

	public Hash getHash() {
		return hash;
	}

	public MerkleInternal getParent() {
		return parent;
	}

	public int getPositionInParent() {
		return positionInParent;
	}

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

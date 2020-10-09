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

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

public abstract class MerkleSynchronizationUtils {

	/**
	 * Get the hash of a node. If the node is null then return the null hash.
	 */
	public static Hash getHash(MerkleNode node) {
		if (node == null) {
			return CryptoFactory.getInstance().getNullHash(MERKLE_DIGEST_TYPE);
		} else if (node.getHash() == null) {
			throw new MerkleSynchronizationException("Tree must be hashed prior to synchronization.");
		} else {
			return node.getHash();
		}
	}

	/**
	 * Get child if it exists, otherwise return null.
	 */
	public static MerkleNode getChild(MerkleNode parent, int childIndex) {
		if (parent == null || parent.isLeaf() || ((MerkleInternal) parent).getNumberOfChildren() <= childIndex) {
			return null;
		}
		return ((MerkleInternal) parent).getChild(childIndex);
	}

	/**
	 * Get the hash of the child if it exists, otherwise return the null hash.
	 */
	public static Hash getChildHash(MerkleNode parent, int childIndex) {
		return getHash(getChild(parent, childIndex));
	}
}

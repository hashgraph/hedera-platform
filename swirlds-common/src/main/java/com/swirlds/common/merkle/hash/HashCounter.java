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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleBreadthFirstIterator;

import java.util.Iterator;

public abstract class HashCounter {
	public static int countMissingHashes(MerkleNode root) {
		int count = 0;
		Iterator<MerkleNode> it = new MerkleBreadthFirstIterator<>(root);
		while (it.hasNext()) {
			MerkleNode node = it.next();
			if (node != null && node.getHash() == null) {
				count++;
			}
		}
		return count;
	}
}

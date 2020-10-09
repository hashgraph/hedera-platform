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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Iterate over a merkle tree in a post-order depth first search. Instead of visiting children
 * from left to right, children are visited in a randomized order. Will make a best effort
 * attempt not to visit nodes that have already been hashed.
 *
 * This iteration strategy is extremely useful for bottom-up algorithms where the result from one subtree
 * does not influence the result from another subtree. Parallel threads can perform work on the tree, and with
 * high probability they will not collide with each other. This strategy has been useful to compute hashes
 * of a tree with many threads in parallel.
 */
public class MerkleRandomHashIterator extends MerkleHashIterator {

	private final Random random;

	public MerkleRandomHashIterator(MerkleNode root, int seed) {
		super(root);
		random = new Random(seed);
	}

	/**
	 * Add children to the stack in a random order.
	 * {@inheritDoc}
	 */
	@Override
	protected void addChildren(final MerkleNode merkleNode) {
		final MerkleInternal node = (MerkleInternal) merkleNode;
		List<Integer> iterationOrder = new ArrayList<>(node.getNumberOfChildren());
		for (int childIndex = 0; childIndex < node.getNumberOfChildren(); childIndex++) {
			iterationOrder.add(childIndex);
		}

		Collections.shuffle(iterationOrder, random);
		for (int childIndex: iterationOrder) {
			pushNode(node.getChild(childIndex));
		}
	}
}

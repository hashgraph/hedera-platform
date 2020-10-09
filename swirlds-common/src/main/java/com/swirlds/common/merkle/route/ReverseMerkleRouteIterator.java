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

package com.swirlds.common.merkle.route;

import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Iterate over a route in a merkle tree in reverse order (from leaf to root).
 */
public class ReverseMerkleRouteIterator implements Iterator<MerkleNode> {

	Iterator<MerkleNode> iterator;

	public ReverseMerkleRouteIterator(MerkleNode root, int[] routeData) {
		LinkedList<MerkleNode> nodes = new LinkedList<>();

		MerkleRouteIterator it = new MerkleRouteIterator(root, routeData);
		while (it.hasNext()) {
			nodes.addFirst(it.next());
		}

		iterator = nodes.iterator();
	}

	@Override
	public boolean hasNext() {
		return iterator.hasNext();
	}

	@Override
	public MerkleNode next() {
		return iterator.next();
	}
}

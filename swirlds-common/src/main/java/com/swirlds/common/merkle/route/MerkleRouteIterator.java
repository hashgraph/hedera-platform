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
import java.util.NoSuchElementException;

/**
 * This iterator walks over each node in a merkle route.
 */
public class MerkleRouteIterator implements Iterator<MerkleNode> {

	private MerkleNode prev;
	private MerkleNode next;

	private int[] steps;
	private int stepIndex;

	public MerkleRouteIterator(MerkleNode root, int[] route) {
		next = root;
		steps = MerkleRoute.convertToStandardIntArray(route);
		stepIndex = 0;
	}

	private void findNext() {
		if (next != null) {
			return;
		}
		if (stepIndex < steps.length) {
			next = MerkleRoute.getChildAtIndex(prev, steps[stepIndex]);
			stepIndex++;
		}

	}

	@Override
	public boolean hasNext() {
		findNext();
		return next != null;
	}

	@Override
	public MerkleNode next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		prev = next;
		next = null;
		return prev;
	}

}

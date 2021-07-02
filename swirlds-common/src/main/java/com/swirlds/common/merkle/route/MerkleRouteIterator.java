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

package com.swirlds.common.merkle.route;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This iterator walks over each node in a merkle route.
 */
public class MerkleRouteIterator implements Iterator<MerkleNode> {

	private MerkleNode prev;
	private MerkleNode next;
	private boolean hasNext;

	private final Iterator<Integer> stepIterator;

	public MerkleRouteIterator(MerkleNode root, MerkleRoute route) {
		next = root;
		hasNext = true;
		stepIterator = route.iterator();
	}

	private static MerkleNode getChildAtIndex(final MerkleNode parent, final int index) {
		if (parent == null) {
			throw new MerkleRouteException("Invalid route, null value prematurely encountered.");
		}
		if (parent.isLeaf()) {
			throw new MerkleRouteException("Invalid route, leaf node prematurely encountered.");
		}
		MerkleInternal internal = parent.cast();
		if (internal.getNumberOfChildren() <= index) {
			throw new MerkleRouteException("Invalid route, index exceeds child count.");
		}
		return internal.getChild(index);
	}

	private void findNext() {
		if (hasNext || !stepIterator.hasNext()) {
			return;
		}

		next = getChildAtIndex(prev, stepIterator.next());
		hasNext = true;
	}

	@Override
	public boolean hasNext() {
		findNext();
		return hasNext;
	}

	@Override
	public MerkleNode next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		prev = next;
		hasNext = false;
		return prev;
	}

	/**
	 * Iterate to the end and return the last element along the route.
	 */
	public MerkleNode getLast() {
		MerkleNode last = next;
		while (hasNext()) {
			last = next();
		}
		return last;
	}

}

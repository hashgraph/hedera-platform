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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This iterator walks through each step in a route.
 */
public class MerkleRouteStepIterator implements Iterator<Integer> {

	/**
	 * The route that is being iterated over.
	 */
	private int[] route;

	/**
	 * The index of the integer in the route that will be read next.
	 */
	private int index;

	/**
	 * The index of the bit within the current integer that will be read next (if next step is binary).
	 */
	private int bitIndex;

	/**
	 * The total number of steps in the current integer.
	 */
	private int stepsInInt;

	/**
	 * The integer data that will be read from next.
	 */
	private int nextData;

	/**
	 * The next value to be returned by the iterator (if not null).
	 */
	private Integer next;

	/**
	 * Create a new iterator.
	 */
	public MerkleRouteStepIterator(int[] route) {
		reset(route);
	}

	protected MerkleRouteStepIterator() {

	}

	/**
	 * Reset the iterator with a new route. Useful for recycling this object.
	 * @param route The new route to iterate.
	 */
	public void reset(int[] route) {
		this.route = route;
		index = 0;
		next = null;
		prepareNextInt();
	}

	/**
	 * Count the number of steps in the next integer and advance the index.
	 */
	private void prepareNextInt() {
		if (route == null) {
			return;
		}
		if (route.length == 0) {
			return;
		}
		nextData = route[index];
		index++;
		if (nextData == 0) {
			throw new MerkleRouteException("Routes should not contain 0s.");
		} else if (nextData > 0) {
			stepsInInt = 1;
		} else {
			stepsInInt = MerkleRoute.getNumberOfStepsInInt(nextData);
			bitIndex = 0;
		}
	}

	private void findNext() {
		if (next != null) {
			return;
		}
		if (route == null) {
			return;
		}

		if (stepsInInt == 0) {
			if (index >= route.length) {
				return;
			}
			prepareNextInt();
		}

		if (nextData > 0) {
			next = nextData;
		} else {
			next = MerkleRoute.getBitAtIndex(nextData, bitIndex + 1);
			bitIndex++;
		}
		stepsInInt--;
	}

	@Override
	public boolean hasNext() {
		findNext();
		return next != null;
	}

	@Override
	public Integer next() {
		findNext();
		if (next == null) {
			throw new NoSuchElementException();
		}
		Integer ret = next;
		next = null;
		return ret;
	}
}

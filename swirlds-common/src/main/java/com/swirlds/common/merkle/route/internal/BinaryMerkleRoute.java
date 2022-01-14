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

package com.swirlds.common.merkle.route.internal;

import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;

import java.util.Arrays;
import java.util.Iterator;

/**
 * A collection of methods for manipulating binary merkle routes.
 *
 * Data format:
 *
 * Routes are encoded as an array of integers.
 *
 * - If an integer in the array is non-zero positive then it represents
 * a step in the route that is greater or equal to 2.
 * - If an integer in the array is negative then it contains one or
 * more binary steps (i.e. 0 or 1).
 * - If an integer in the array is 0 then it contains no data and is not permitted in a route.
 *
 * The binary format of a negative integer is as follows:
 *
 * 1XXXXXX...10000
 * | |       |  |
 * | |       |  |-- 0 or more 0s (does not encode any steps)
 * | |       |
 * | |       |-- A single 1 (does not encode a step)
 * | |
 * | |-- A sequence of 0s or 1s representing binary steps
 * |
 * |-- A leading 1 (does not encode a step)
 */
public class BinaryMerkleRoute extends AbstractMerkleRoute {

	/**
	 * The number of binary steps that can be stored in each integer.
	 */
	private static final int CAPACITY_PER_INT = Integer.SIZE - 2;

	// all 0s with a 1 in the lowest order bit: 000...0001
	private static final int LOWEST_ORDER_BIT_MASK = 1;

	// all 1s: 111...111
	private static final int ALL_ONES_MASK = -1;

	// a 1 in the highest order bit followed by 0s: 1000...000
	private static final int NEW_BINARY_DATA_INT = 1 << (Integer.SIZE - 1);

	private static final int[] emptyData = new int[0];

	private final int[] data;

	public BinaryMerkleRoute() {
		data = emptyData;
	}

	protected BinaryMerkleRoute(final BinaryMerkleRoute baseRoute, final int step) {
		data = copyAndExpandIfNeeded(baseRoute.data, step);
		addStepToRouteData(data, step);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		int length = 0;

		for (final int datum : data) {
			if (datum > 0) {
				length++;
			} else if (datum < 0) {
				length += getNumberOfStepsInInt(datum);
			}
		}

		return length;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Integer> iterator() {
		return new BinaryMerkleRouteIterator(this.data);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleRoute extendRoute(final int step) {
		return new BinaryMerkleRoute(this, step);
	}

	/**
	 * Get the bit at a particular index within an integer.
	 * @param data
	 * 		an int value
	 * @param index
	 * 		the index of the bit in concern
	 * @return the bit at the given index within the given integer
	 */
	public static int getBitAtIndex(final int data, final int index) {
		if (index < 0 || index >= Integer.SIZE) {
			throw new IndexOutOfBoundsException();
		}

		final int mask = LOWEST_ORDER_BIT_MASK << (Integer.SIZE - index - 1);

		return (mask & data) == 0 ? 0 : 1;
	}

	/**
	 * Get a new int by setting the bit at a particular index within an integer to be a particular value
	 *
	 * @param data
	 * 		an int value
	 * @param index
	 * 		the index of the bit in concern
	 * @param value
	 * 		new value of the bit
	 * @return a new int value after setting
	 */
	private static int setBitAtIndex(final int data, final int index, final int value) {
		if (index < 0 || index >= Integer.SIZE) {
			throw new IndexOutOfBoundsException();
		}

		int mask = LOWEST_ORDER_BIT_MASK << (Integer.SIZE - index - 1);

		if (value == 0) {
			mask = ALL_ONES_MASK ^ mask;
			return data & mask;
		} else if (value == 1) {
			return data | mask;
		} else {
			throw new IllegalArgumentException("A bit may only hold a 0 or a 1");
		}
	}

	/**
	 * Deep copy route data, expanding the copy if necessary to add the specified step.
	 * More efficient than first copying and then expanding.
	 *
	 * @param routeData
	 * 		the route data to be deep copied
	 * @param step
	 * 		the binary step that the resulting route must have the capacity to store
	 * @return a deep copy of the route with the capacity for the step
	 */
	private static int[] copyAndExpandIfNeeded(final int[] routeData, final int step) {
		if (hasCapacityForStep(routeData, step)) {
			return Arrays.copyOf(routeData, routeData.length);
		} else {
			return Arrays.copyOf(routeData, routeData.length + 1);
		}
	}

	/**
	 * Determine the remaining capacity for steps in an integer.
	 *
	 * @param intData
	 * 		an integer
	 * @return remaining capacity for steps in an integer
	 */
	private static int getRemainingCapacityInInt(int intData) {
		if (intData == 0) {
			// The first bit is reserved for the negative sign, the last bit is used to show the number of steps
			return CAPACITY_PER_INT;
		}
		int capacity = 0;
		final int mask = LOWEST_ORDER_BIT_MASK;
		while ((intData & mask) == 0) {
			capacity++;
			intData = intData >> 1;
		}
		return capacity;
	}

	/**
	 * Check if route data has capacity for a given step. More efficient than {@link #getRemainingCapacityInInt(int)}.
	 *
	 * @param routeData
	 * 		the route data to be checked
	 * @param step
	 * 		the step to add to the route.
	 */
	private static boolean hasCapacityForStep(final int[] routeData, final int step) {
		if (routeData.length == 0) {
			return false;
		}

		int lastIntInData = routeData[routeData.length - 1];

		if (step > 1) {
			return lastIntInData == 0;
		}

		if (lastIntInData > 0) {
			return false;
		} else {
			return (lastIntInData & LOWEST_ORDER_BIT_MASK) == 0;
		}
	}

	/**
	 * Add a step which is a 0 or a 1. Assumes that the binary route has capacity for the step.
	 *
	 * @param routeData
	 * 		the route data to add a step to. Will be modified by this function.
	 * @param step
	 * 		the binary step to add to the route data.
	 */
	private static void addBinaryStepToRouteData(final int[] routeData, final int step) {
		int lastIntInData = routeData[routeData.length - 1];

		// Ensure left flag is properly set
		if (lastIntInData == 0) {
			lastIntInData = NEW_BINARY_DATA_INT;
		}

		final int index = CAPACITY_PER_INT - getRemainingCapacityInInt(routeData[routeData.length - 1]) + 1;

		// Set the bit
		lastIntInData = setBitAtIndex(lastIntInData, index, step);

		// Set the right-most flag bit
		lastIntInData = setBitAtIndex(lastIntInData, index + 1, 1);

		routeData[routeData.length - 1] = lastIntInData;
	}

	/**
	 * Add a step which is greater than 1. Assumes that the binary route has capacity for the step.
	 *
	 * @param routeData
	 * 		the route data to add a step to. Will be modified by this function.
	 * @param step
	 * 		the n-ary step to add to the route data.
	 */
	private static void addNaryStepToRouteData(final int[] routeData, final int step) {
		routeData[routeData.length - 1] = step;
	}

	/**
	 * Add a step to a binary route. Assumes that the binary route has capacity for the step.
	 * @param route
	 * 		the route to add a step to. Will be modified by this function.
	 * @param step
	 * 		the step to add to the route.
	 */
	private static void addStepToRouteData(final int[] route, final int step) {
		if (step < 0) {
			throw new MerkleRouteException("Binary route steps can not be negative.");
		} else if (step < 2) {
			addBinaryStepToRouteData(route, step);
		} else {
			addNaryStepToRouteData(route, step);
		}
	}

	/**
	 * Calculate the number of steps contained within an integer.
	 *
	 * @param data
	 * 		an integer
	 * @return the number of steps contained within the integer
	 */
	public static int getNumberOfStepsInInt(final int data) {
		return CAPACITY_PER_INT - getRemainingCapacityInInt(data);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final BinaryMerkleRoute that = (BinaryMerkleRoute) o;
		return Arrays.equals(data, that.data);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}
}

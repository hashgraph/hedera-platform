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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;
import java.util.List;

/**
 * Methods for reading and manipulating a merkle route.
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
 * | |       |  |--&gt; 0 or more 0s (does not encode any steps)
 * | |       |
 * | |       |--&gt; A single 1 (does not encode a step)
 * | |
 * | |--&gt; A sequence of 0s or 1s representing binary steps
 * |
 * |--&gt; A leading 1 (does not encode a step)
 */
public abstract class MerkleRoute implements Iterable<Integer> {

	/**
	 * The number of bits in a java integer.
	 */
	private static final int BITS_PER_INT = 32;

	/**
	 * The number of binary steps that can be stored in each integer.
	 */
	private static final int CAPACITY_PER_INT = BITS_PER_INT - 2;

	private static final int MASK1 = 0b00000000_00000000_00000000_00000001;
	private static final int MASK2 = 0b11111111_11111111_11111111_11111111;
	private static final int NEW_BINARY_DATA_INT = 0b10000000_00000000_00000000_00000000;

	private static ThreadLocal<MerkleRouteStepIterator> localStepIterator1 = new ThreadLocal<>();
	private static ThreadLocal<MerkleRouteStepIterator> localStepIterator2 = new ThreadLocal<>();

	/**
	 * Returns a step iterator.
	 *
	 * The same object is recycled each time this method is called on a particular thread.
	 * This allows for utility methods in this class to use iterators without paying the cost of creating and
	 * destroying an object.
	 */
	private static Iterator<Integer> getThreadLocalRouteIterator1(int[] route) {
		MerkleRouteStepIterator iterator = localStepIterator1.get();
		if (iterator == null) {
			iterator = new MerkleRouteStepIterator();
			localStepIterator1.set(iterator);
		}
		iterator.reset(route);
		return iterator;
	}

	/**
	 * Returns a step iterator.
	 *
	 * The same object is recycled each time this method is called on a particular thread.
	 * This allows for utility methods in this class to use iterators without paying the cost of creating and
	 * destroying an object.
	 */
	private static Iterator<Integer> getThreadLocalRouteIterator2(int[] route) {
		MerkleRouteStepIterator iterator = localStepIterator2.get();
		if (iterator == null) {
			iterator = new MerkleRouteStepIterator();
			localStepIterator2.set(iterator);
		}
		iterator.reset(route);
		return iterator;
	}

	/**
	 * Get an empty route -- i.e. a route from a node to itself.
	 */
	public static int[] emptyRoute() {
		return new int[0];
	}

	/**
	 * Construct a route from a list of steps.
	 */
	public static int[] buildRoute(List<Integer> steps) {
		int[] route = emptyRoute();
		for (int step : steps) {
			route = expandIfNeeded(route, step);
			addStepToRoute(route, step);
		}
		return route;
	}

	/**
	 * Make a deep copy of a route.
	 *
	 * @param route
	 * 		The route to copy.
	 * @return A deep copy of the given route.
	 */
	public static int[] deepCopyRoute(int[] route) {
		if (route == null) {
			return emptyRoute();
		} else {
			int[] copy = new int[route.length];
			System.arraycopy(route, 0, copy, 0, route.length);
			return copy;
		}
	}

	/**
	 * Increase the capacity of a route.
	 *
	 * @param route
	 * 		The route to expand.
	 * @return The route returned will contain the same data as the provided route but will have expanded capacity.
	 */
	private static int[] addRouteCapacity(int[] route) {
		int length = 0;
		if (route != null) {
			length = route.length;
		}
		int[] expandedRoute = new int[length + 1];
		if (route != null) {
			System.arraycopy(route, 0, expandedRoute, 0, length);
		}
		return expandedRoute;
	}

	/**
	 * Expand the route if needed so that it has capacity for the step.
	 */
	private static int[] expandIfNeeded(int[] route, int step) {
		if (hasCapacityForStep(route, step)) {
			return route;
		} else {
			return addRouteCapacity(route);
		}
	}

	/**
	 * Deep copy the route, expanding it if necessary to add the specified step.
	 * More efficient than first copying and then expanding.
	 */
	private static int[] copyAndExpandIfNeeded(int[] route, int step) {
		if (hasCapacityForStep(route, step)) {
			return deepCopyRoute(route);
		} else {
			return addRouteCapacity(route);
		}
	}

	/**
	 * Get the bit at a particular index within a 2 byte integer.
	 */
	protected static int getBitAtIndex(int data, int index) {
		if (index < 0 || index >= BITS_PER_INT) {
			throw new IndexOutOfBoundsException();
		}

		int mask = MASK1;
		mask = mask << (BITS_PER_INT - index - 1);

		return (mask & data) == 0 ? 0 : 1;
	}

	private static int setBitAtIndex(int data, int index, int value) {
		if (index < 0 || index >= BITS_PER_INT) {
			throw new IndexOutOfBoundsException();
		}

		int mask = MASK1 << (BITS_PER_INT - index - 1);

		if (value == 0) {
			mask = MASK2 ^ mask;
			return data & mask;
		} else if (value == 1) {
			return data | mask;
		} else {
			throw new IllegalArgumentException("A bit may only hold a 0 or a 1");
		}
	}

	/**
	 * Determine the remaining capacity in an integer.
	 */
	private static int getRemainingCapacity(int data) {
		if (data == 0) {
			// The first bit is reserved for the negative sign, the last bit is used to show the number of steps
			return CAPACITY_PER_INT;
		}
		int capacity = 0;
		final int mask = 0b00000000_00000000_00000000_00000001;
		while ((data & mask) == 0) {
			capacity++;
			data = data >> 1;
		}
		return capacity;
	}

	/**
	 * Check if an integer has capacity for a given step. More efficient than {@link #getRemainingCapacity(int)}.
	 */
	private static boolean hasCapacityForStep(int[] route, int step) {
		if (route == null || route.length == 0) {
			return false;
		}

		int data = route[route.length - 1];

		if (step > 1) {
			return data == 0;
		}

		if (data > 0) {
			return false;
		} else {
			return (data & MASK1) == 0;
		}
	}

	/**
	 * Add a step which is a 0 or a 1. Assumes that the route has capacity for the step.
	 */
	private static void addBinaryStepToRoute(int[] route, int step) {
		int data = route[route.length - 1];

		// Ensure left flag is properly set
		if (data == 0) {
			data = NEW_BINARY_DATA_INT;
		}

		int index = CAPACITY_PER_INT - getRemainingCapacity(route[route.length - 1]) + 1;

		// Set the bit
		data = setBitAtIndex(data, index, step);

		// Set the right-most flag bit
		data = setBitAtIndex(data, index + 1, 1);

		route[route.length - 1] = data;
	}

	/**
	 * Add a step which is greater than 1. Assumes that the route has capacity for the step.
	 */
	private static void addNaryStepToRoute(int[] route, int step) {
		route[route.length - 1] = step;
	}

	/**
	 * Add a step to a route. Assumes that the route has capacity for the step.
	 */
	private static void addStepToRoute(int[] route, int step) {
		if (step < 0) {
			throw new MerkleRouteException("Route steps can not be negative.");
		} else if (step < 2) {
			addBinaryStepToRoute(route, step);
		} else {
			addNaryStepToRoute(route, step);
		}
	}

	/**
	 * Add a step to a route without modifying the original route.
	 *
	 * @param route
	 * 		The route to add a step to. Will not be modified by this function.
	 * @param step
	 * 		The step to add to the route.
	 * @return The updated route.
	 */
	public static int[] extendRoute(int[] route, int step) {
		route = copyAndExpandIfNeeded(route, step);
		addStepToRoute(route, step);
		return route;
	}

	/**
	 * Return the number of steps in a route.
	 */
	public static int getRouteLength(int[] route) {
		if (route == null) {
			return 0;
		}

		int length = 0;

		for (int data : route) {
			if (data > 0) {
				length++;
			} else if (data < 0) {
				length += getNumberOfStepsInInt(data);
			}
		}

		return length;
	}

	/**
	 * Calculate the number of steps contained within an integer.
	 */
	protected static int getNumberOfStepsInInt(int data) {
		return CAPACITY_PER_INT - getRemainingCapacity(data);
	}

	/**
	 * Convert an encoded route into an array of integers where each integer encodes a single step.
	 */
	public static int[] convertToStandardIntArray(int[] route) {
		if (route == null) {
			return new int[0];
		}
		int[] expandedRoute = new int[getRouteLength(route)];
		Iterator<Integer> iterator = getThreadLocalRouteIterator1(route);
		for (int index = 0; index < expandedRoute.length; index++) {
			expandedRoute[index] = iterator.next();
		}
		return expandedRoute;
	}

	protected static MerkleNode getChildAtIndex(MerkleNode parent, int index) {
		if (parent == null) {
			throw new MerkleRouteException("Invalid route, null value prematurely encountered.");
		}
		if (parent.isLeaf()) {
			throw new MerkleRouteException("Invalid route, leaf node prematurely encountered.");
		}
		MerkleInternal internal = (MerkleInternal) parent;
		if (internal.getNumberOfChildren() <= index) {
			throw new MerkleRouteException("Invalid route, index exceeds child count.");
		}
		return internal.getChild(index);
	}

	/**
	 * Get the node at the end of a route. More efficient than using
	 * {@link com.swirlds.common.merkle.iterators.MerkleIterator} if the goal is to get the node at the end of the
	 * route.
	 *
	 * @param start
	 * 		The starting node.
	 * @param route
	 * 		The route to follow.
	 * @return The merkle node at the specified position.
	 */
	public static MerkleNode getNodeAtRoute(MerkleNode start, int[] route) {
		if (route == null) {
			return start;
		}
		MerkleNode node = start;
		Iterator<Integer> iterator = getThreadLocalRouteIterator1(route);
		while (iterator.hasNext()) {
			node = getChildAtIndex(node, iterator.next());
		}
		return node;
	}

	/**
	 * Get a string representation of a route.
	 */
	public static String getRouteString(int[] route) {
		int[] expandedRoute = convertToStandardIntArray(route);
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int index = 0; index < expandedRoute.length; index++) {
			sb.append(expandedRoute[index]);
			if (index + 1 < expandedRoute.length) {
				sb.append(" > ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

//	/**
//	 * Trees that are built top-down will have correct route information. Trees built bottom-up will not.
//	 * This method iterates over a merkle tree and sets the correct route for each node.
//	 *
//	 * @param root
//	 * 		The root of a merkle tree.
//	 */
//	public static void buildRoutesInTree(MerkleNode root) {
//		buildRoutesInTree(null, 0, root);
//	}

//	/**
//	 * Trees that are built top-down will have correct route information. Trees built bottom-up will not.
//	 * This method iterates over a merkle tree and sets the correct route for each node.
//	 *
//	 * @param parentRoute
//	 * 		The parent route of the root (if the root is the root of a sub-tree).
//	 * @param indexInParent
//	 * 		The index of this node within its parent.
//	 * @param node
//	 * 		The root of a merkle tree / sub-tree.
//	 */
//	public static void buildRoutesInTree(int[] parentRoute, int indexInParent, MerkleNode node) {
//		if (node == null) {
//			return;
//		}
//
//		node.setRoute(MerkleRoute.extendRoute(parentRoute, indexInParent));
//
//		if (!node.isLeaf() && ((MerkleInternal) node).getNumberOfChildren() > 0) {
//			Iterator<MerkleNode> iterator = new MerkleBreadthFirstIterator<>(node);
//			while (iterator.hasNext()) {
//				MerkleNode next = iterator.next();
//				if (next != null && !next.isLeaf()) {
//					MerkleInternal parent = (MerkleInternal) next;
//					for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
//						MerkleNode child = parent.getChild(childIndex);
//						if (child != null) {
//							child.setRoute(MerkleRoute.extendRoute(parent.getRoute(), childIndex));
//						}
//					}
//				}
//			}
//		}
//	}

	/**
	 * Compare two routes.
	 *
	 * - Returns -1 if routeA is to the left of routeB.
	 * - Returns 0 when routeA is an ancestor or descendant of routeB, or the routes are exactly the same.
	 * - Returns 1 routeA is to the right of routeB.
	 */
	public static int compare(int[] routeA, int[] routeB) {
		Iterator<Integer> iteratorA = getThreadLocalRouteIterator1(routeA);
		Iterator<Integer> iteratorB = getThreadLocalRouteIterator2(routeB);

		while (iteratorA.hasNext() && iteratorB.hasNext()) {
			int a = iteratorA.next();
			int b = iteratorB.next();
			if (a < b) {
				return -1;
			} else if (b < a) {
				return 1;
			}
		}

		// No differences found
		return 0;
	}

}


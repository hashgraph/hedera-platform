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

package com.swirlds.common.merkle.route;

import java.util.Iterator;

/**
 * A MerkleRoute describes a path through a merkle tree.
 * Each "step" in the route describes the child index to traverse next within the tree.
 *
 * MerkleRoute objects are immutable after creation. No operation should be capable of modifying an existing route.
 *
 * Implementations of MerkleRoute are expected to override equals() and hashCode().
 */
public interface MerkleRoute extends Comparable<MerkleRoute>, Iterable<Integer> {

	/**
	 * @return an iterator that walks over the steps in the route
	 */
	@Override
	Iterator<Integer> iterator();

	/**
	 * Get the number of steps in the route. A node at the root of a tree will have a route of size 0.
	 *
	 * This operation may be more expensive than O(1) for some route implementations (for example, some implementations
	 * that are compressed may compute the size as needed so the size doesn't need to be stored in memory).
	 */
	int size();

	/**
	 * Create a new route that shares all steps of this route but with an additional step at the end.
	 *
	 * @param step
	 * 		the step to add to the new route
	 * @return a new route
	 */
	MerkleRoute extendRoute(final int step);

	/**
	 * Compare this route to another. Will return -1 if this route is "to the left" of the other route, 1 if this route
	 * is "to the right" of the other route, and 0 if this route is an ancestor, descendant, or the same as the other
	 * route.
	 *
	 * To determine if a route is to the left or the right of another route, find a route that is the last common
	 * ancestor of both (i.e. the longest route that matches the beginnings of both routes). On the last common ancestor
	 * find the two children that are ancestors of each of the routes. Route A is to the left of route B if the ancestor
	 * of route A is to the left of the ancestor of route B on the last common ancestor.
	 */
	@Override
	int compareTo(final MerkleRoute that);

	/**
	 * Returns true if a given route is an ancestor of this route. A route is considered to be an ancestor to itself.
	 *
	 * @param that
	 * 		a route to compare to
	 * @return true if the given route is an ancestor of this route
	 */
	boolean isAncestorOf(final MerkleRoute that);

	/**
	 * Returns true if a given route is a descendant of this route. A route is considered to be a descendant to itself.
	 *
	 * @param that
	 * 		a route to compare to
	 * @return true if the given route is a descendant of this route
	 */
	boolean isDescendantOf(final MerkleRoute that);
}


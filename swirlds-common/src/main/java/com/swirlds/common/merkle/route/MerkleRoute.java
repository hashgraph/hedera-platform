/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.merkle.route;

import com.swirlds.common.io.SelfSerializable;
import java.util.Iterator;
import java.util.List;

/**
 * A MerkleRoute describes a path through a merkle tree. Each "step" in the route describes the
 * child index to traverse next within the tree.
 *
 * <p>MerkleRoute objects are immutable after creation (with the exception of deserialization). No
 * operation should be capable of modifying an existing route.
 *
 * <p>Although merkle routes are capable of being serialized, it is strongly advised that routes are
 * not included in the serialization of the state. The binary format of merkle routes is subject to
 * change at a future date, and the inclusion of a route in the state could lead to complications
 * during migration. Implementations of MerkleRoute are expected to override equals() and
 * hashCode().
 */
public interface MerkleRoute extends Comparable<MerkleRoute>, Iterable<Integer>, SelfSerializable {

    /** The maximum length that a route is permitted to be. */
    int MAX_ROUTE_LENGTH = 1024;

    /**
     * @return an iterator that walks over the steps in the route
     */
    @Override
    Iterator<Integer> iterator();

    /**
     * Get the number of steps in the route. A node at the root of a tree will have a route of size
     * 0.
     *
     * <p>This operation may be more expensive than O(1) for some route implementations (for
     * example, some implementations that are compressed may compute the size as needed so the size
     * doesn't need to be stored in memory).
     */
    int size();

    /**
     * Check if this route is the empty route.
     *
     * @return true if this route has 0 steps
     */
    boolean isEmpty();

    /**
     * Create a new route that shares all steps of this route but with an additional step at the
     * end.
     *
     * @param step the step to add to the new route
     * @return a new route
     */
    MerkleRoute extendRoute(final int step);

    /**
     * Create a new route that shares all steps of this route but with additional steps at the end.
     *
     * @param steps the steps to add to the new route
     * @return a new route
     */
    MerkleRoute extendRoute(final List<Integer> steps);

    /**
     * Create a new route that shares all steps of this route but with additional steps at the end.
     *
     * @param steps teh steps to add to the new route
     * @return a new route
     */
    MerkleRoute extendRoute(final int... steps);

    /**
     * Compare this route to another. Will return -1 if this route is "to the left" of the other
     * route, 1 if this route is "to the right" of the other route, and 0 if this route is an
     * ancestor, descendant, or the same as the other route.
     *
     * <p>To determine if a route is to the left or the right of another route, find a route that is
     * the last common ancestor of both (i.e. the longest route that matches the beginnings of both
     * routes). On the last common ancestor find the two children that are ancestors of each of the
     * routes. Route A is to the left of route B if the ancestor of route A is to the left of the
     * ancestor of route B on the last common ancestor.
     */
    @Override
    int compareTo(final MerkleRoute that);

    /**
     * Returns true if a given route is an ancestor of this route. A route is considered to be an
     * ancestor to itself.
     *
     * @param that a route to compare to
     * @return true if the given route is an ancestor of this route
     */
    boolean isAncestorOf(final MerkleRoute that);

    /**
     * Returns true if a given route is a descendant of this route. A route is considered to be a
     * descendant to itself.
     *
     * @param that a route to compare to
     * @return true if the given route is a descendant of this route
     */
    boolean isDescendantOf(final MerkleRoute that);

    /**
     * Get the step at a particular index.
     *
     * <p>May be O(n) where n is the length of the route for some implementations.
     *
     * @param index the index of the step to fetch. Negative values index from the end of the list,
     *     with -1 referencing the last step, -2 referencing the second to last step, and so on.
     * @return the requested step
     * @throws IndexOutOfBoundsException if the requested index is invalid
     */
    int getStep(final int index);
}

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
package com.swirlds.common.merkle.interfaces;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;
import java.util.List;

/** A type that is the parent of other merkle nodes. */
public interface MerkleParent {

    /** The maximum number of children that a MerkleInternal node can have */
    int MAX_CHILD_COUNT_UBOUND = 64;

    /** The minimum number of children that a MerkleInternal node can have */
    int MIN_CHILD_COUNT = 0;

    /**
     * Returns the number of all immediate children that are a MerkleNode object.
     *
     * <p>This function must not change the number of children or modify any children in any way.
     *
     * @return number of children
     */
    int getNumberOfChildren();

    /**
     * Returns deterministically the child at position <i>index</i>
     *
     * <p>This function must not change the number of children or modify any children in any way.
     *
     * <p>If a child at an index that does not violate {@link #getMaximumChildCount()} is requested
     * but that child has never been set, then null should be returned.
     *
     * @param index The position to look for the child.
     * @param <T> the type of the child
     * @return the child at the position <i>index</i>
     * @throws com.swirlds.common.merkle.exceptions.IllegalChildIndexException if the index is
     *     negative or if the index is greater than {@link #getMaximumChildCount()}.
     */
    <T extends MerkleNode> T getChild(final int index);

    /**
     * Set the child at a particular index.
     *
     * <p>Additionally, method is expected to perform the following operations:
     *
     * <ul>
     *   <li>invalidating this node's hash
     *   <li>decrementing the reference count of the displaced child (if any)
     *   <li>incrementing the reference count of the new child (if not null)
     *   <li>updating the route of the new child (if not null)
     * </ul>
     *
     * @param index The position where the child will be inserted.
     * @param child A MerkleNode object.
     * @throws MutabilityException if the child is immutable
     * @throws MerkleRouteException if the child's route is changed in an illegal manner
     */
    default void setChild(final int index, final MerkleNode child) {
        setChild(index, child, null, false);
    }

    /**
     * Set the child at a particular index. This method provides extra configuration and is designed
     * for advanced use cases.
     *
     * <p>Uses a precomputed route for the child. Useful for setting a child where the route is
     * already known without paying the performance penalty of recreating the route.
     *
     * <p>Additionally, method is expected to perform the following operations:
     *
     * <ul>
     *   <li>invalidating this node's hash
     *   <li>decrementing the reference count of the displaced child (if any)
     *   <li>incrementing the reference count of the new child (if not null)
     *   <li>updating the route of the new child (if not null)
     * </ul>
     *
     * @param index The position where the child will be inserted.
     * @param child A MerkleNode object.
     * @param childRoute A precomputed route. No error checking, assumed to be correct.
     * @param childMayBeImmutable if true then the child may be immutable. If false then it is
     *     assumed that the child is mutable, and if the child is immutable then an exception is
     *     thrown
     * @throws MutabilityException if the child is immutable and childMayBeImmutable is false
     * @throws MerkleRouteException if the child's route is changed in an illegal manner
     */
    void setChild(
            final int index,
            final MerkleNode child,
            final MerkleRoute childRoute,
            final boolean childMayBeImmutable);

    /**
     * The minimum number of children that this node may have.
     *
     * @return The minimum number of children at the specified version.
     */
    default int getMinimumChildCount() {
        return MIN_CHILD_COUNT;
    }

    /**
     * The maximum number of children that this node may have.
     *
     * <p>The value returned must not be greater than MAX_CHILD_COUNT. If nodes violate this then
     * reconnect might break.
     *
     * @return The maximum number of children at the specified version.
     */
    default int getMaximumChildCount() {
        return MAX_CHILD_COUNT_UBOUND;
    }

    /**
     * Adds children that have been deserialized from a stream.
     *
     * @param children A list of children.
     * @param version The version of the node when these children were serialized.
     */
    default void addDeserializedChildren(List<MerkleNode> children, final int version) {
        for (int childIndex = 0; childIndex < children.size(); childIndex++) {
            setChild(childIndex, children.get(childIndex));
        }
    }
}

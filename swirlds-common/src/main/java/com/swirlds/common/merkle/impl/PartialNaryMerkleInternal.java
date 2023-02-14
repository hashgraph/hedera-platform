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
package com.swirlds.common.merkle.impl;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.impl.internal.AbstractMerkleInternal;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements boilerplate functionality for an N-Ary {@link MerkleInternal} (i.e. an
 * internal node with a variable number of children &gt; 2). Classes that implement {@link
 * MerkleInternal} are not required to extend a class such as this or {@link
 * PartialBinaryMerkleInternal}, but absent a reason it is recommended to do so in order to avoid
 * re-implementation of this code.
 */
public non-sealed class PartialNaryMerkleInternal extends AbstractMerkleInternal
        implements PartialMerkleInternal {

    private final ArrayList<MerkleNode> children;

    public PartialNaryMerkleInternal() {
        children = new ArrayList<>(Math.min(MIN_CHILD_COUNT, getMinimumChildCount()));
    }

    /**
     * Classes that inherit from AbstractMerkleLeaf are required to call super() in their
     * constructors.
     *
     * @param initialSize Indicates the number of children links to be initially created, can be
     *     resized, but that is slow, so it is far better to pick the right size from the outset if
     *     you can.
     */
    protected PartialNaryMerkleInternal(final int initialSize) {
        if (initialSize > MAX_CHILD_COUNT_UBOUND) {
            throw new IllegalChildIndexException(
                    MIN_CHILD_COUNT, MAX_CHILD_COUNT_UBOUND, initialSize);
        }
        children = new ArrayList<>(initialSize);
    }

    /** Copy constructor. */
    protected PartialNaryMerkleInternal(final PartialNaryMerkleInternal that) {
        super(that);
        children = new ArrayList<>(that.getNumberOfChildren());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Return the current number of children for this node. Some or all can be null.
     */
    @Override
    public int getNumberOfChildren() {
        return children.size();
    }

    /**
     * {@inheritDoc}
     *
     * @param index The position to look for a child.
     * @param <T> the type of the child
     * @return The child found at that position.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends MerkleNode> T getChild(final int index) {
        if (children.size() <= index || index < 0) {
            checkChildIndexIsValid(index);
            return null;
        }
        return (T) children.get(index);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Select a child using an index number. This will throw an error if an illegal value is
     * used.
     *
     * @param index which child position is going to be updated
     * @param child replacement merkle node for that position
     */
    @Override
    protected void setChildInternal(final int index, final MerkleNode child) {
        children.set(index, child);
    }

    /**
     * @param index expand the array of children to include index
     */
    @Override
    protected void allocateSpaceForChild(final int index) {
        for (int i = children.size(); i <= index; i++) {
            children.add(null);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param children this is an array of children to be added
     * @param version serialization version (specifying format to be used)
     *     <p>Can't make this final because PTT overrides it
     */
    @Override
    public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
        for (final MerkleNode child : this.children) {
            if (child != null) {
                child.release();
            }
        }
        this.children.clear();
        super.addDeserializedChildren(children, version);
    }

    /**
     * check whether the requested index is in valid range [0, maximum child count), if not throw an
     * {@link IllegalChildIndexException}
     *
     * @param index requested index of a child
     */
    @Override
    protected void checkChildIndexIsValid(final int index) {
        int maxSize = Math.min(getMaximumChildCount(), MAX_CHILD_COUNT_UBOUND);
        // note, if the maxSize is 0 this will always throw
        if (index < 0 || index >= maxSize) {
            throw new IllegalChildIndexException(0, maxSize - 1, index);
        }
    }
}

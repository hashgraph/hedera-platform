/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle.utility;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;

import java.util.ArrayList;
import java.util.List;

/**
 * This abstract class implements boiler plate functionality for an N-Ary tree {@link MerkleInternal}
 * (i.e. an internal node with a variable number of children &gt; 2). Classes that implement (@link MerkleInternal}
 * are not required to extend an abstract class such as this or {@link AbstractBinaryMerkleInternal}, but absent
 * a reason it is recommended to do so in order to avoid re-implementation of this code.
 */
public abstract class AbstractNaryMerkleInternal
		extends AbstractMerkleInternal {

	private final ArrayList<MerkleNode> children;

	/**
	 * Constructor that calls super() with a _true_ forcing it to bounds check the MIN_CHILD_COUNT,
	 * MAX_CHILD_COUNT_LBOUND, and MAX_CHILD_COUNT_UBOUND.
	 */
	protected AbstractNaryMerkleInternal() {
		super(true);
		children = new ArrayList<>(Math.min(MIN_CHILD_COUNT, getMinimumChildCount(getVersion())));
	}

	/**
	 * Classes that inherit from AbstractMerkleLeaf are required to call super() in their constructors.
	 *
	 * @param initialSize
	 * 		Indicates the number of children links to be initially created, can be resized, but that is slow,
	 * 		so it is far better to pick the right size from the outset if you can.
	 */
	protected AbstractNaryMerkleInternal(final int initialSize) {
		super(true);
		if (initialSize > MerkleInternal.MAX_CHILD_COUNT_UBOUND) {
			throw new IllegalChildIndexException(MIN_CHILD_COUNT, MAX_CHILD_COUNT_UBOUND, initialSize);
		}
		children = new ArrayList<>(initialSize);
	}

	/**
	 * Copy constructor.
	 */
	protected AbstractNaryMerkleInternal(final AbstractNaryMerkleInternal that) {
		super(that);
		children = new ArrayList<>(that.getNumberOfChildren());
	}

	/**
	 * {@inheritDoc}
	 *
	 * Return the current number of children for this node.  Some or all can be null.
	 */
	@Override
	public int getNumberOfChildren() {
		return children.size();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param index
	 * 		The position to look for a child.
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
	 * Select a child using an index number. This will throw an error if an illegal value is used.
	 *
	 * @param index
	 * 		which child position is going to be updated
	 * @param child
	 * 		replacement merkle node for that position
	 */
	@Override
	protected final void setChildInternal(final int index, final MerkleNode child) {
		children.set(index, child);
	}

	/**
	 * @param index
	 * 		expand the array of children to include index
	 */
	@Override
	protected final void allocateSpaceForChild(final int index) {
		for (int i = children.size(); i <= index; i++) {
			children.add(null);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param children
	 * 		this is an array of children to be added
	 * @param version
	 * 		serialization version (specifying format to be used)
	 *
	 * 		Can't make this final because PTT overrides it
	 */
	@Override
	public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
		this.children.clear();
		super.addDeserializedChildren(children, version);
	}

	/**
	 * check whether the requested index is in valid range [0, maximum child count),
	 * if not throw an {@link IllegalChildIndexException}
	 *
	 * @param index
	 * 		requested index of a child
	 */
	@Override
	protected final void checkChildIndexIsValid(final int index) {
		int maxSize = Math.min(getMaximumChildCount(getVersion()), MerkleInternal.MAX_CHILD_COUNT_UBOUND);
		// note, if the maxSize is 0 this will always throw
		if (index < 0 || index >= maxSize) {
			throw new IllegalChildIndexException(0, maxSize - 1, index);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractNaryMerkleInternal copy();
}

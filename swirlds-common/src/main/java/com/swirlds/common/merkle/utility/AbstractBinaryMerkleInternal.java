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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;

/**
 * This abstract class implements boiler plate functionality for a binary {@link MerkleInternal}
 * (i.e. an internal node with 2 or fewer children). Classes that implement (@link MerkleInternal}
 * are not required to extend an abstract class such as this or {@link AbstractNaryMerkleInternal},
 * but absent a reason it is recommended to do so in order to avoid re-implementation of this code.
 */
public abstract class AbstractBinaryMerkleInternal extends AbstractMerkleInternal {

	private MerkleNode left;
	private MerkleNode right;

	private static final int MIN_BINARY_CHILD_COUNT = 0;
	private static final int BINARY_CHILD_COUNT = 2;

	private static class ChildIndices {
		public static final int LEFT = 0;
		public static final int RIGHT = 1;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Binary constructor does not verify MIN_CHILD_COUNT, MAX_CHILD_COUNT_LBOUND, or MAX_CHILD_COUNT_UBOUND.
	 */
	protected AbstractBinaryMerkleInternal() {
		super(false);
	}

	/**
	 * Copy constructor.
	 */
	protected AbstractBinaryMerkleInternal(final AbstractBinaryMerkleInternal other) {
		super(other);
	}

	/**
	 * {@inheritDoc}
	 *
	 * In the binary case this is always BINARY_CHILD_COUNT (2).
	 */
	@Override
	public final int getNumberOfChildren() {
		//binary tree, we always have two children, even if null
		return BINARY_CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 *
	 * In the binary case this is always BINARY_CHILD_COUNT (2).
	 */
	@Override
	public final int getMinimumChildCount(final int version) {
		return MIN_BINARY_CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 *
	 * In the binary case this is always BINARY_CHILD_COUNT (2).
	 *
	 * @param version
	 * 		The version in question.
	 * @return always BINARY_CHILD_COUNT (2), even if the children are null
	 */
	@Override
	public final int getMaximumChildCount(final int version) {
		return BINARY_CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 *
	 * If the index is either LEFT or RIGHT, then return the correct child
	 * otherwise return an IllegalChildIndexException.
	 *
	 * @param index
	 * 		The position to look for the child.
	 * @param <T>
	 * 		the type of the child
	 * @return the child node is returned
	 */
	@SuppressWarnings("unchecked")
	@Override
	public final <T extends MerkleNode> T getChild(final int index) {
		if (index == ChildIndices.LEFT) {
			return (T) left;
		} else if (index == ChildIndices.RIGHT) {
			return (T) right;
		} else {
			throw new IllegalChildIndexException(ChildIndices.LEFT, ChildIndices.RIGHT, index);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * Select either the ChildIndices.LEFT (0) or the ChildIndices.RIGHT (1) using an index number.
	 * This will throw an error if a different value is used.*
	 *
	 * @param index
	 * 		which child position is going to be updated
	 * @param child
	 */
	@Override
	protected final void setChildInternal(final int index, final MerkleNode child) {
		if (index == ChildIndices.LEFT) {
			left = child;
		} else if (index == ChildIndices.RIGHT) {
			right = child;
		} else { //bad index
			throw new IllegalChildIndexException(ChildIndices.LEFT, ChildIndices.RIGHT, index);
		}
	}

	/**
	 * Set the left child.
	 *
	 * @param left
	 * 		a merkle node that will become this node's left child
	 * @param <T>
	 * 		the type of the child
	 */
	public <T extends MerkleNode> void setLeft(final T left) {
		setChild(ChildIndices.LEFT, left);
	}

	/**
	 * Get the left child.
	 *
	 * @param <T>
	 * 		the type of the left child
	 * @return the merkle node in the left child position, or null if no such node is present
	 */
	@SuppressWarnings("unchecked")
	public <T extends MerkleNode> T getLeft() {
		return (T) left;
	}

	/**
	 * Set the right child.
	 *
	 * @param right
	 * 		a merkle node that will become this node's right child
	 * @param <T>
	 * 		the type of the child
	 */
	public <T extends MerkleNode> void setRight(final T right) {
		setChild(ChildIndices.RIGHT, right);
	}

	@SuppressWarnings("unchecked")
	public <T extends MerkleNode> T getRight() {
		return (T) right;
	}

	/**
	 * {@inheritDoc}
	 *
	 * In the N-Ary case, this will increase the number of children to the value provided.  In this binary
	 * case, there are always two children, so this is a NOP.
	 *
	 * @param index
	 * 		unused
	 */
	@Override
	protected final void allocateSpaceForChild(final int index) {
		//in the binary case, these children are members of the object and don't need to be allocated or resized
	}


	/**
	 * {@inheritDoc}
	 *
	 * In the N-Ary case, this verifies that the index is between 0 and the number of actual children
	 * (including null children) of the node.  However, in the binary case, there are always two:
	 * ChildIndices.LEFT (0) and ChildIndices.RIGHT (1), and the subsequent call is to getChild(index), above,
	 * which also tests the legality of the index.  As a result, there is no need to perform an extra
	 * bounds test here.  This is effectively a NOP.
	 *
	 * @param index
	 */
	@Override
	protected final void checkChildIndexIsValid(final int index) {
		//the index is actually being tested in getChild(), which will throw if not ChildIndices.LEFT or ChildIndices.RIGHT
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractBinaryMerkleInternal copy();
}

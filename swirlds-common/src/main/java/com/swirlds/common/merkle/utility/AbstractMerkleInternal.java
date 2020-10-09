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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.exceptions.IllegalChildTypeException;

import java.util.ArrayList;
import java.util.List;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleInternal}. Classes that implement
 * {@link MerkleInternal} are not required to extend this class, but absent a reason it is recommended to avoid
 * re-implementation of this code.
 */
public abstract class AbstractMerkleInternal extends AbstractMerkleNode implements MerkleInternal {

	private ArrayList<MerkleNode> children;

	public AbstractMerkleInternal() {
		children = new ArrayList<>(getMinimumChildCount(getVersion()) == 0 ? 2 : getMinimumChildCount(getVersion()));
	}

	/**
	 * Classes that inherit from AbstractMerkleLeaf are required to call super() in their constructors.
	 */
	public AbstractMerkleInternal(int initialSize) {
		children = new ArrayList<>(initialSize);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return children.size();
	}

	/**
	 * {@inheritDoc}
	 *
	 * If the child at the requested index is within the allowed bounds as specified by getMaximumChildCount
	 * but a child has never been inserted at that index, return null. If the requested index is
	 * less than 0 or not less than the maximum child count then return an IllegalChildIndexException.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public final <T extends MerkleNode> T getChild(int index) {
		if (children.size() <= index || index < 0) {
			checkChildIndexIsValid(index);
			return null;
		}
		return (T) children.get(index);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Note: this method is not final to allow the FCMap to track parents. Eventually this method will become final.
	 */
	@Override
	public void setChild(int index, MerkleNode child) {
		checkChildIndexIsValid(index);

		long classId = NULL_CLASS_ID;
		if (child != null) {
			classId = child.getClassId();
		}

		if (!childHasExpectedType(index, classId, getVersion())) {
			throw new IllegalChildTypeException(index, classId, getVersion(), getClassId());
		}

		// When children change the hash needs to be invalidated
		invalidateHash();

		for (int i = children.size(); i <= index; i++) {
			children.add(null);
		}

		MerkleNode oldChild = children.get(index);
		if (oldChild == child) {
			return;
		}

		// Decrement the reference count of the original child
		if (oldChild != null) {
			oldChild.decrementReferenceCount();
		}

		// Increment the reference count of the new child
		if (child != null) {
			child.incrementReferenceCount();
		}

		children.set(index, child);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addDeserializedChildren(List<MerkleNode> children, final int version) {
		this.children = new ArrayList<>(children.size());
		for (int childIndex = 0; childIndex < children.size(); childIndex++) {
			setChild(childIndex, children.get(childIndex));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized final void release() {
		if (getReferenceCount() > 0) {
			throw new IllegalStateException("Nodes can only be deleted when the reference count equals 0.");
		}
		if (!isReleased()) {
			onRelease();
			markAsReleased();
			for (MerkleNode child: children) {
				if (child != null) {
					child.decrementReferenceCount();
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final Hash getHash() {
		return super.getHash();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setHash(Hash hash) {
		super.setHash(hash);
	}

	/**
	 * check whether the requested index is in valid range [0, maximum child count),
	 * if not throw an {@link IllegalChildIndexException}
	 *
	 * @param index
	 * 		requested index of a child
	 */
	private void checkChildIndexIsValid(final int index) {
		int maxSize = Math.min(getMaximumChildCount(getVersion()), MerkleInternal.MAX_CHILD_COUNT);
		if (index < 0 || index >= maxSize) {
			throw new IllegalChildIndexException(0, maxSize - 1, index);
		}
	}
}

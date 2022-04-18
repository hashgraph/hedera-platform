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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.MutabilityException;
import com.swirlds.common.ReferenceCountException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildBoundsException;
import com.swirlds.common.merkle.exceptions.IllegalChildTypeException;
import com.swirlds.common.merkle.io.SerializationStrategy;
import com.swirlds.common.merkle.route.MerkleRoute;

import java.util.List;
import java.util.Set;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.merkle.io.SerializationStrategy.DEFAULT_MERKLE_INTERNAL;
import static com.swirlds.common.merkle.utility.MerkleUtils.merkleDebugString;

/**
 * This abstract class implements boiler plate functionality for a binary {@link MerkleInternal} (i.e. an internal
 * node with 2 or fewer children). Classes that implement (@link MerkleInternal} are not required to extend an
 * abstract class such as this or {@link AbstractNaryMerkleInternal}, but absent a reason it is recommended to do so
 * in order to avoid re-implementation of this code.
 */
public abstract class AbstractMerkleInternal extends AbstractMerkleNode implements MerkleInternal {

	private static final Set<SerializationStrategy> DEFAULT_STRATEGIES = Set.of(DEFAULT_MERKLE_INTERNAL);

	/**
	 * Constructor for AbstractMerkleInternal.  Optional bounds testing.
	 *
	 * @param checkChildConstraints
	 * 		true = test min/max bounds for the number of children,
	 * 		false = don't (skip the performance hit in the BinaryMerkle case)
	 */
	protected AbstractMerkleInternal(final boolean checkChildConstraints) {
		if (checkChildConstraints) {
			final int version = getVersion();
			final int min = getMinimumChildCount(version);
			final int max = getMaximumChildCount(version);

			if (!((min >= MIN_CHILD_COUNT)
					&& (max >= MAX_CHILD_COUNT_LBOUND)
					&& (max >= min)
					&& (max <= MAX_CHILD_COUNT_UBOUND))) {
				throw new IllegalChildBoundsException(MIN_CHILD_COUNT, MAX_CHILD_COUNT_UBOUND);
			}
		}
	}

	/**
	 * Copy constructor. Initializes internal variables and copies the route. Does not copy children or other metadata.
	 */
	protected AbstractMerkleInternal(final AbstractMerkleInternal that) {
		super(that);
	}

	/**
	 * This is an implementation specific version of setChild.
	 *
	 * @param index
	 * 		which child position is going to be updated
	 * @param child
	 * 		new node to attach
	 */
	protected abstract void setChildInternal(final int index, final MerkleNode child);

	/**
	 * Allow N-Ary and Binary Merkle classes to make space as appropriate.
	 *
	 * @param index
	 * 		in the N-Ary case, expand the array to accommodate this many children
	 * 		in the Binary case, this is a NOP
	 */
	protected abstract void allocateSpaceForChild(final int index);

	/**
	 * Check whether the requested index is in valid range [0, maximum child count).
	 * In the Binary case, this is a NOP as the error testing happens in getChild(index).
	 *
	 * @param index
	 * 		- child position to verify is legal
	 */
	protected abstract void checkChildIndexIsValid(final int index);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void setChild(
			final int index,
			final MerkleNode child,
			final MerkleRoute childRoute,
			final boolean childMayBeImmutable) {

		throwIfInvalidState();

		checkChildIndexIsValid(index);

		throwIfInvalidChild(index, child, childMayBeImmutable);

		allocateSpaceForChild(index);

		final MerkleNode oldChild = getChild(index);
		if (oldChild == child) {
			return;
		}
		// When children change the hash needs to be invalidated.
		// Self hashing nodes are required to manage their own hash invalidation.
		if (!isSelfHashing()) {
			invalidateHash();
		}

		// Decrement the reference count of the original child
		if (oldChild != null) {
			oldChild.decrementReferenceCount();
		}

		if (child != null) {
			// Increment the reference count of the new child
			child.incrementReferenceCount();

			if (childRoute == null) {
				child.setRoute(computeRouteForChild(index));
			} else {
				child.setRoute(childRoute);
			}
		}

		setChildInternal(index, child);
	}

	/**
	 * Check if a potential child is valid, and throw if it is not valid
	 *
	 * @param index
	 * 		the index of the child
	 * @param child
	 * 		the child to be added
	 * @param childMayBeImmutable
	 * 		if true then the child is permitted to be immutable, if false then it is not permitted
	 */
	private void throwIfInvalidChild(final int index, final MerkleNode child, final boolean childMayBeImmutable) {
		long classId = NULL_CLASS_ID;

		if (child != null) {
			classId = child.getClassId();

			if (!childMayBeImmutable && child.isImmutable()) {
				throw new MutabilityException("Immutable child can not be added to parent. parent = "
						+ merkleDebugString(this) + ", child = " + merkleDebugString(child));
			}
		}

		if (!childHasExpectedType(index, classId, getVersion())) {
			throw new IllegalChildTypeException(index, classId, getVersion(), getClassId());
		}
	}

	private void throwIfInvalidState() {
		if (isImmutable()) {
			throw new MutabilityException("Can not set child on immutable parent. " + merkleDebugString(this));
		}
		if (isReleased()) {
			throw new ReferenceCountException("Can not set child on released parent. " + merkleDebugString(this));
		}
	}

	/**
	 * Compute and construct a new route for a child at a given position.
	 * Recycles the route of the existing child if possible.
	 *
	 * @param index
	 * 		the index of the child
	 */
	private MerkleRoute computeRouteForChild(final int index) {
		MerkleRoute childRoute = null;
		if (getNumberOfChildren() > index) {
			final MerkleNode oldChild = getChild(index);
			if (oldChild != null) {
				childRoute = oldChild.getRoute();
			}
		}
		if (childRoute == null) {
			childRoute = getRoute().extendRoute(index);
		}
		return childRoute;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Deserialize from a list of children
	 *
	 * @param children
	 * 		A list of children.
	 * @param version
	 * 		Version (e.g. format) of the deserialized data.
	 */
	@Override
	public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
		for (int childIndex = 0; childIndex < children.size(); childIndex++) {
			setChild(childIndex, children.get(childIndex));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void releaseInternal() {
		onRelease();
		for (int index = 0; index < getNumberOfChildren(); index++) {
			final MerkleNode child = getChild(index);
			if (child != null) {
				child.decrementReferenceCount();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invalidateHash() {
		setHash(null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<SerializationStrategy> supportedSerialization(final int version) {
		return DEFAULT_STRATEGIES;
	}

	/**
	 * {@inheritDoc}
	 *
	 * WARNING: setting the route on an internal node with children requires a full iteration of the subtree.
	 * For large trees this may be very expensive.
	 */
	@Override
	public final void setRoute(final MerkleRoute route) {
		if (!getRoute().equals(route)) {
			super.setRoute(route);
			// If there are children, fix the routes of the children.
			for (int index = 0; index < getNumberOfChildren(); index++) {
				final MerkleNode child = getChild(index);
				if (child != null) {
					child.setRoute(getRoute().extendRoute(index));
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractMerkleInternal copy();
}

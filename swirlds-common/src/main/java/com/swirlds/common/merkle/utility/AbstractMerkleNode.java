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

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.ReferenceCountException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;

import java.beans.Transient;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static com.swirlds.common.merkle.route.MerkleRouteFactory.getEmptyRoute;
import static com.swirlds.common.merkle.utility.MerkleUtils.merkleDebugString;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleNode}.
 *
 * Merkle classes should not directly inherit this class. They should instead inherit either
 * {@link AbstractMerkleLeaf} or {@link AbstractBinaryMerkleInternal} or {@link AbstractNaryMerkleInternal}.
 */
public abstract class AbstractMerkleNode extends AbstractHashable implements MerkleNode {

	private boolean immutable;

	private MerkleRoute route;

	protected final AtomicInteger referenceCount;

	protected AbstractMerkleNode() {
		referenceCount = new AtomicInteger(0);
		immutable = false;
		route = getEmptyRoute();
	}

	protected AbstractMerkleNode(final AbstractMerkleNode that) {
		referenceCount = new AtomicInteger(0);
		this.route = that.getRoute();
	}

	/**
	 * This lambda method is used to update the atomic integer reference count when
	 * {@link #incrementReferenceCount()} is called.
	 */
	private static final IntUnaryOperator increment = current -> {
		if (current < 0) {
			// node destroyed, do not increment
			return current;
		} else {
			// node has reference count reduced by 1
			return current + 1;
		}
	};

	/**
	 * This lambda method is used to update the atomic integer reference count when
	 * {@link #decrementReferenceCount()} is called.
	 */
	private static final IntUnaryOperator decrement = current -> {
		if (current <= 0) {
			// node destroyed or node has no references, do not increment
			return current;
		} else if (current == 1) {
			// node is being destroyed
			return -1;
		} else {
			// node has reference count reduced by 1
			return current - 1;
		}
	};

	/**
	 * This lambda method is used to update the atomic integer reference count when
	 * {@link #release()} is called.
	 */
	private static final IntUnaryOperator release = current -> {
		if (current != 0) {
			// current is destroyed or has outstanding explicit references, do not change value
			return current;
		} else {
			// mark node as destroyed
			return -1;
		}
	};

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isImmutable() {
		return immutable;
	}

	/**
	 * Specify the immutability status of the node.
	 *
	 * @param immutable
	 * 		if this node should be immutable
	 */
	@Transient
	protected final void setImmutable(final boolean immutable) {
		this.immutable = immutable;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void incrementReferenceCount() {
		final int previousValue = referenceCount.getAndUpdate(increment);
		if (previousValue < 0) {
			throw new ReferenceCountException("object can not be reserved after it has been destroyed");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void decrementReferenceCount() {
		final int previousValue = referenceCount.getAndUpdate(decrement);
		if (previousValue < 0) {
			throw new ReferenceCountException("object can not released after it has been destroyed");
		}
		if (previousValue == 0) {
			throw new ReferenceCountException("object can not have reference count decremented if it is 0");
		}
		if (previousValue == 1) {
			releaseInternal();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void release() {
		final int previousValue = referenceCount.getAndUpdate(release);
		if (previousValue < 0) {
			throw new ReferenceCountException("object can not released after it has been destroyed");
		}
		if (previousValue > 0) {
			throw new ReferenceCountException(
					"Nodes can only be released when the reference count equals 0. count = " + previousValue);
		}
		releaseInternal();
	}

	/**
	 * Release any external resources or references on resources held by this node.
	 */
	protected void releaseInternal() {
		onRelease();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getReferenceCount() {
		return referenceCount.get();
	}

	/**
	 * Check if this node has been released.
	 */
	@Override
	public final boolean isReleased() {
		return referenceCount.get() == -1;
	}

	/**
	 * This method is responsible for releasing external data not handled by the java garbage collector
	 * which is being used by this node. This method will be called no more than once on a given object.
	 *
	 * This method should not release data that is currently in use by other nodes. It should also not
	 * delete or otherwise effect other nodes.
	 */
	protected void onRelease() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final MerkleRoute getRoute() {
		return route;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setRoute(final MerkleRoute route) {
		if (!(getRoute() == route || getRoute().equals(route)) && getReferenceCount() > 1) {
			// If you see this exception, the most likely culprit is that you are attempting to "move" a merkle
			// node from one position to another but the merkle node has multiple parents. If this operation was
			// allowed to proceed, a node in multiple trees would have the incorrect route in at least one of those
			// trees. Instead of moving the node, make a copy and move the copy.
			throw new MerkleRouteException(
					"Routes can not be set unless the reference count is 0 or 1. " + merkleDebugString(this));
		}
		this.route = route;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractMerkleNode copy();
}

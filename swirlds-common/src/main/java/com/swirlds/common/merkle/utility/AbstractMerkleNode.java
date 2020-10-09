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

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.merkle.MerkleNode;

import java.beans.Transient;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This abstract class implements boiler plate functionality for a {@link MerkleNode}.
 *
 * Merkle classes should not directly inherit this class. They should instead inherit either
 * {@link AbstractMerkleLeaf} or {@link AbstractMerkleInternal}.
 */
abstract class AbstractMerkleNode extends AbstractHashable implements MerkleNode {

	private boolean immutable;

//	private int[] route;

	private final AtomicInteger referenceCount;

	private boolean released;

	public AbstractMerkleNode() {
		referenceCount = new AtomicInteger(0);
		released = false;
	}

	public final boolean isImmutable() {
		return this.immutable;
	}

	@Transient
	protected final void setImmutable(final boolean immutable) {
		this.immutable = immutable;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void incrementReferenceCount() {
		referenceCount.getAndIncrement();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void decrementReferenceCount() {
		if (referenceCount.decrementAndGet() == 0) {
			release();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final int getReferenceCount() {
		return referenceCount.get();
	}

	/**
	 * Check if this node has been released.
	 */
	public final boolean isReleased() {
		return released;
	}

	/**
	 * Mark this node as having been released.
	 */
	protected final void markAsReleased() {
		released = true;
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
	
//	/**
//	 * {@inheritDoc}
//	 */
//	@Override
//	public int[] getRoute() {
//		return route;
//	}
//
//	/**
//	 * {@inheritDoc}
//	 */
//	@Override
//	public void setRoute(int[] path) {
//		this.route = path;
//	}
}

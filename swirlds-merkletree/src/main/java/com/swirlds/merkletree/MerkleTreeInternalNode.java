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

package com.swirlds.merkletree;

import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;

public final class MerkleTreeInternalNode
		extends AbstractBinaryMerkleInternal {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0x1b1c07ad7dc65f17L;

	public MerkleTreeInternalNode() {
		super();
	}

	private MerkleTreeInternalNode(final MerkleTreeInternalNode sourceNode) {
		super(sourceNode);
		setImmutable(false);
		sourceNode.setImmutable(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MerkleTreeInternalNode copy() {
		throwIfImmutable();
		throwIfReleased();
		return new MerkleTreeInternalNode(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}

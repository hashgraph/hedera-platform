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

package com.swirlds.fcmap.internal;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;

public final class FCMInternalNode<K extends MerkleNode, V extends MerkleNode>
		extends AbstractBinaryMerkleInternal
		implements FCMNode<K, V> {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0x1b1c07ad7dc65f17L;

	private static class ChildIndices {
		public static final int LEFT_CHILD = 0;
		public static final int RIGHT_CHILD = 1;

		public static final int CHILD_COUNT = 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean childHasExpectedType(int index, long childClassId, int version) {
		return childClassId == FCMInternalNode.CLASS_ID ||
				childClassId == FCMLeaf.CLASS_ID ||
				childClassId == NULL_CLASS_ID;
	}

	public void setLeftChild(FCMNode<K, V> newLeftChild) {
		setChild(ChildIndices.LEFT_CHILD, newLeftChild);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMNode<K, V> getLeftChild() {
		return getChild(ChildIndices.LEFT_CHILD);
	}

	public void setRightChild(FCMNode<K, V> newRightChild) {
		setChild(ChildIndices.RIGHT_CHILD, newRightChild);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMNode<K, V> getRightChild() {
		return getChild(ChildIndices.RIGHT_CHILD);
	}

	public FCMInternalNode() {
		super();
	}

	private FCMInternalNode(final FCMInternalNode<K, V> sourceNode) {
		super(sourceNode);
		setImmutable(false);
		sourceNode.setImmutable(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public BalanceInfo getBalanceInfo() {
		final BalanceInfo leftBalanceInfo = getLeftChild() == null ? new BalanceInfo(true, 0)
				: getLeftChild().getBalanceInfo();
		final BalanceInfo rightBalanceInfo = getRightChild() == null ? new BalanceInfo(true, 0)
				: getRightChild().getBalanceInfo();
		final boolean isBalanced = leftBalanceInfo.isBalanced() && rightBalanceInfo.isBalanced()
				&& Math.abs(leftBalanceInfo.getMaxHeight() - rightBalanceInfo.getMaxHeight()) < 2;

		return new BalanceInfo(isBalanced,
				1 + Math.max(leftBalanceInfo.getMaxHeight(), rightBalanceInfo.getMaxHeight()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMInternalNode<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new FCMInternalNode<>(this);
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFCMLeaf() {
		return false;
	}
}

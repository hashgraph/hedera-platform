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

package com.swirlds.fcmap.internal;

import com.swirlds.common.FCMKey;
import com.swirlds.common.FCMValue;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_CLASS_ID;

public final class FCMInternalNode<K extends FCMKey, V extends FCMValue>
		extends AbstractFCMNode<K, V> {

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	public static final long CLASS_ID = 0x1b1c07ad7dc65f17L;

	private static final Marker INTERNAL_NODE = MarkerManager.getMarker("FCM_INTERNAL_NODE");

	private static final Logger LOG = LogManager.getLogger(FCMInternalNode.class);

	private static final String DELETE_MESSAGE = "FCMInternalNode has been deleted";

	private static class ChildIndices {
		public static final int LEFT_CHILD = 0;
		public static final int RIGHT_CHILD = 1;

		public static final int CHILD_COUNT = 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaximumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
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
	@SuppressWarnings("unchecked")
	public void setChild(int index, MerkleNode child) {
		throwIfImmutable();
		super.setChild(index, child);
		if (child != null) {
			final FCMNode<K, V> node = (FCMNode<K, V>) child;
			node.setParent(this);
		}
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
		super(sourceNode.getParent());
		this.setLeftChild(sourceNode.getLeftChild());
		this.setRightChild(sourceNode.getRightChild());
		setImmutable(false);
		sourceNode.setImmutable(true);
	}

	private Hash getLeftChildHash() {
		if (getLeftChild() == null) {
			return null;
		}

		return getLeftChild().getHash();
	}

	private Hash getRightChildHash() {
		if (getRightChild() == null) {
			return null;
		}

		return getRightChild().getHash();
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

	public FCMInternalNode<K, V> createNewNodeWithOnlyLeftChild() {
		final FCMInternalNode<K, V> newNode = new FCMInternalNode<>();
		final FCMNode<K, V> leftChild = this.getLeftChild();
		newNode.setLeftChild(leftChild);
		newNode.setParent(this.getParent());
		leftChild.setParent(newNode);
		return newNode;
	}

	public FCMInternalNode<K, V> createNewNodeWithRightChildAsLeftChild() {
		final FCMInternalNode<K, V> newNode = new FCMInternalNode<>();
		final FCMNode<K, V> newLeftChild = this.getRightChild();
		newNode.setLeftChild(newLeftChild);
		newLeftChild.setParent(newNode);
		return newNode;
	}

	public FCMInternalNode<K, V> createNewNodeWithChildReplaced(final FCMNode<K, V> oldChild,
			final FCMNode<K, V> newChild) {
		if (this.isReleased()) {
			throw new IllegalStateException("Parent FCMInternalNode is already deleted: " + this);
		}

		final FCMInternalNode<K, V> newNode = new FCMInternalNode<>();

		if (getLeftChild() == oldChild) {
			newNode.setLeftChild(newChild);
			newNode.setRightChild(getRightChild());
		} else {
			newNode.setLeftChild(getLeftChild());
			newNode.setRightChild(newChild);
		}

		newNode.setParent(this.getParent());
		if (newNode.getLeftChild() != null) {
			newNode.getLeftChild().setParent(newNode);
		}

		if (newNode.getRightChild() != null) {
			newNode.getRightChild().setParent(newNode);
		}

		return newNode;
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
	public void copyFrom(final SerializableDataInputStream inStream) {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) {

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

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

import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.MerkleLong;

import java.util.Iterator;
import java.util.function.Consumer;

import static com.swirlds.common.merkle.copy.MerkleCopy.adoptChildren;
import static com.swirlds.common.merkle.copy.MerkleCopy.copyTreeToLocation;
import static com.swirlds.common.merkle.copy.MerklePathReplacement.replacePath;
import static com.swirlds.common.merkle.utility.MerkleUtils.findChildPositionInParent;

/**
 * Fast Copyable Merkle Tree
 *
 * @param <K>
 * 		Type for keys
 * @param <V>
 * 		Type for values
 */
public class FCMTree<K extends MerkleNode, V extends MerkleNode> extends AbstractBinaryMerkleInternal {

	public static final long CLASS_ID = 0x84bec080aa5cfeecL;

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * Keeps track of the right most leaf in order to replace a deleted
	 * leaf with it. Once the replacement is done, a new right most leaf
	 * must be assigned.
	 */
	private FCMLeaf<K, V> rightMostLeaf;

	private static class ChildIndices {
		/**
		 * Number of leaves in the tree.
		 */
		public static final int SIZE = 0;
		/**
		 * Root of the tree. For every mutation, a new root is set.
		 */
		public static final int ROOT = 1;

		public static final int CHILD_COUNT = 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean childHasExpectedType(int index, long childClassId, int version) {
		switch (index) {
			case ChildIndices.SIZE:
				return childClassId == MerkleLong.CLASS_ID;
			case ChildIndices.ROOT:
				return childClassId == FCMInternalNode.CLASS_ID;
			default:
				throw new IllegalChildIndexException(getMinimumChildCount(getVersion()),
						getMaximumChildCount(getVersion()), version);
		}
	}

	private MerkleLong getSize() {
		return getChild(ChildIndices.SIZE);
	}

	public void setSize(MerkleLong size) {
		throwIfImmutable();
		setChild(ChildIndices.SIZE, size);
	}

	public FCMInternalNode<K, V> getRoot() {
		return getChild(ChildIndices.ROOT);
	}

	private void setRoot(final FCMInternalNode<K, V> root) {
		setChild(ChildIndices.ROOT, root);

		this.invalidateHash();
	}

	/**
	 * Creates an instance of {@link FCMTree}
	 * {@link SerializableHashable}
	 */
	public FCMTree() {
		this(new MerkleLong(0), new FCMInternalNode<>());
	}

	private FCMTree(final MerkleLong size, final FCMInternalNode<K, V> root) {
		super();
		setSize(size);
		setRoot(root);
	}

	protected FCMTree(final FCMTree<K, V> fcmTreeSource) {
		super(fcmTreeSource);
		this.setSize(fcmTreeSource.getSize().copy());

		// Create a new instance of the root, use references to all data below root
		this.setRoot(new FCMInternalNode<>());
		adoptChildren(fcmTreeSource.getRoot(), getRoot());

		this.rightMostLeaf = fcmTreeSource.rightMostLeaf;
		fcmTreeSource.rightMostLeaf = null;

		setImmutable(false);
		fcmTreeSource.setImmutable(true);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public FCMTree<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new FCMTree<>(this);
	}

	/**
	 * Clears the tree in O(1).
	 */
	public void clear() {
		throwIfImmutable();
		setSize(new MerkleLong(0));
		this.setRoot(new FCMInternalNode<>());
	}

	/**
	 * Finds a leaf by iterating over the leaves.
	 * This takes O(n), where n is the number of leaves
	 * and worst case requires traversing the whole tree.
	 *
	 * @param key
	 * 		Key to find
	 * @return The leaf if found, null otherwise
	 */
	public FCMLeaf<K, V> findLeafByKey(final K key) {
		if (key == null) {
			throw new NullPointerException("Null keys are not allowed");
		}

		final Iterator<FCMLeaf<K, V>> leafIterator = this.leafIterator();
		while (leafIterator.hasNext()) {
			final FCMLeaf<K, V> leaf = leafIterator.next();
			if (leaf.getKey().equals(key)) {
				return leaf;
			}
		}

		return null;
	}

	/**
	 * Deletes a leaf from the tree.
	 *
	 * @param leafToDelete
	 * 		Leaf to be removed from the tree
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	public void delete(final FCMLeaf<K, V> leafToDelete, final Consumer<FCMLeaf<K, V>> updateCache) {
		throwIfImmutable();
		if (leafToDelete == null) {
			throw new NullPointerException("Delete does not support null leaves");
		}

		if (getSize().getValue() < 3) {
			this.deleteLeafFromBasicTree(leafToDelete, updateCache);
		} else {
			this.deleteLeaf(leafToDelete, updateCache);
		}

		getSize().decrement();
		this.setRightMostLeaf();
	}

	/**
	 * Delete the right most leaf.
	 *
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	@SuppressWarnings("unchecked")
	private void deleteRightMostLeaf(final Consumer<FCMLeaf<K, V>> updateCache) {

		// Replace a path from the root down to the grandparent of the rightmost leaf.
		// Don't bother replacing parent here, sibling will replace parent.
		final MerkleNode[] path = replacePath(getRoot(), rightMostLeaf.getRoute(), 2);

		final FCMInternalNode<K, V> parentOfRightMostLeaf = path[path.length - 2].cast();
		final FCMLeaf<K, V> siblingOfRightMostLeaf = parentOfRightMostLeaf.getLeftChild().cast();
		final FCMInternalNode<K, V> grandparentOfRightMostLeaf = path[path.length - 3].cast();
		final int indexOfParentInGrandparent =
				findChildPositionInParent(grandparentOfRightMostLeaf, parentOfRightMostLeaf);

		// Move the sibling up to replace its parent
		final FCMLeaf<K, V> copy = copyTreeToLocation(
				grandparentOfRightMostLeaf,
				indexOfParentInGrandparent,
				siblingOfRightMostLeaf);

		updateCache.accept(copy);
	}

	/**
	 * Delete the sibling of the right most leaf.
	 *
	 * @param path
	 * 		a path of nodes that have been replaced down to the parent of the right most leaf
	 * @param parentOfRightMostLeaf
	 * 		the parent of the right most leaf, pre-computed by the caller
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	@SuppressWarnings("unchecked")
	private void deleteRightMostLeafSibling(
			final MerkleNode[] path,
			final MerkleNode parentOfRightMostLeaf,
			final Consumer<FCMLeaf<K, V>> updateCache) {

		// Note: path has been replaced by caller

		final FCMInternalNode<K, V> grandparentOfRightMostLeaf = path[path.length - 3].cast();

		final int indexOfParentInGrandparent =
				findChildPositionInParent(grandparentOfRightMostLeaf, parentOfRightMostLeaf);

		// Move the right most leaf up to replace its parent.
		final FCMLeaf<K, V> copy = copyTreeToLocation(grandparentOfRightMostLeaf, indexOfParentInGrandparent, rightMostLeaf);
		updateCache.accept(copy);
	}

	/**
	 * Delete a leaf that is neither the right most leaf nor its sibling.
	 */
	private void deleteMiddleLeaf(
			final FCMInternalNode<K, V> parentOfNodeToDelete,
			final FCMLeaf<K, V> leafToDelete,
			final Consumer<FCMLeaf<K, V>> updateCache) {

		final int indexOfChildToDelete = findChildPositionInParent(parentOfNodeToDelete, leafToDelete);

		// Move the right most leaf to replace the leaf being deleted
		final FCMLeaf<K, V> copy = copyTreeToLocation(parentOfNodeToDelete, indexOfChildToDelete, rightMostLeaf);
		updateCache.accept(copy);

		// Move the sibling of the rightmost leaf to replace its parent.
		// Since we already have copied the rightmost leaf, it is safe to simply delete the original tree.
		deleteRightMostLeaf(updateCache);
	}

	/**
	 * Delete a leaf that is not the right most leaf.
	 */
	@SuppressWarnings("unchecked")
	private void deleteNonRightMostLeaf(final FCMLeaf<K, V> leafToDelete, final Consumer<FCMLeaf<K, V>> updateCache) {

		// Replace a path from the root down to the parent of the leaf to delete.
		final MerkleNode[] path = replacePath(getRoot(), leafToDelete.getRoute(), 1);
		final FCMInternalNode<K, V> parentOfNodeToDelete = path[path.length - 2].cast();

		if (parentOfNodeToDelete.getRightChild() == rightMostLeaf) {
			// We are deleting the sibling of the right most leaf.
			deleteRightMostLeafSibling(path, parentOfNodeToDelete, updateCache);
		} else {
			deleteMiddleLeaf(parentOfNodeToDelete, leafToDelete, updateCache);
		}
	}

	/**
	 * Deletes a leaf.
	 *
	 * @param leafToDelete
	 * 		leaf to delete from the tree
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	private void deleteLeaf(final FCMLeaf<K, V> leafToDelete, final Consumer<FCMLeaf<K, V>> updateCache) {
		if (rightMostLeaf == leafToDelete) {
			// Leaf deleted is right most leaf.
			deleteRightMostLeaf(updateCache);
		} else {
			// Leaf deleted is not the right most leaf.
			deleteNonRightMostLeaf(leafToDelete, updateCache);
		}
	}

	/**
	 * Deletes a leaf from a tree with 1 or 2 leaves.
	 *
	 * @param leafToDelete
	 * 		Leaf to delete from the tree
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	private void deleteLeafFromBasicTree(
			final FCMLeaf<K, V> leafToDelete,
			final Consumer<FCMLeaf<K, V>> updateCache) {

		if (getSize().getValue() < 1) {
			throw new IllegalStateException("The tree is empty. No leaf to delete");
		} else if (getSize().getValue() == 1) {
			getRoot().setLeftChild(null);
			rightMostLeaf = null;
		} else if (getSize().getValue() == 2) {
			if (rightMostLeaf.equals(leafToDelete)) {
				// we are deleting the rightmost leaf
				getRoot().setRightChild(null);
				rightMostLeaf = getRoot().getLeftChild().cast();
			} else {
				// we are deleting the sibling of the rightmost leaf
				rightMostLeaf = copyTreeToLocation(getRoot(), 0, rightMostLeaf);
				updateCache.accept(rightMostLeaf);
				getRoot().setRightChild(null);
			}
		}
	}

	/**
	 * After a deletion is performed, the right most leaf changed, and must be set again.
	 */
	private void setRightMostLeaf() {
		if (getSize().getValue() < 3) {
			final FCMInternalNode<K, V> root = this.getRoot();
			final FCMNode<K, V> child = root.getRightChild() != null ? root.getRightChild() : root.getLeftChild();
			rightMostLeaf = child == null ? null : child.cast();
		} else {
			final long leftMostOneBit = BitUtil.findLeftMostBit(getSize().getValue() - 1);
			FCMNode<K, V> node = this.findFirstLeafAtCompleteSubtree(leftMostOneBit, getSize().getValue() - 1,
					this.getRoot());
			rightMostLeaf = node.getRightChild().cast();
		}
	}

	/**
	 * Replaces an old leaf with a new leaf.
	 *
	 * @param oldLeaf
	 * 		Old leaf to replace
	 * @param newLeaf
	 * 		New leaf replacing
	 */
	@SuppressWarnings("unchecked")
	public void update(final FCMLeaf<K, V> oldLeaf, final FCMLeaf<K, V> newLeaf) {
		throwIfImmutable();

		if (oldLeaf == null || newLeaf == null) {
			throw new NullPointerException("Update does not support null leaves");
		} else if (getSize().getValue() < 1) {
			throw new IllegalStateException("The tree is empty. No leaf to replace");
		}

		// Replace a path down to the parent of the old leaf. Don't bother replacing the old leaf itself.
		final MerkleNode[] path = replacePath(getRoot(), oldLeaf.getRoute(), 1);

		final FCMInternalNode<K, V> parent = path[path.length - 2].cast();
		final int indexOfChildInParent = findChildPositionInParent(parent, oldLeaf);

		parent.setChild(indexOfChildInParent, newLeaf, oldLeaf.getRoute());

		if (rightMostLeaf == oldLeaf) {
			rightMostLeaf = newLeaf;
		}
	}

	/**
	 * Get an FCMLeaf with a value that is safe to modify. Does not make the key safe to modify.
	 *
	 * @param originalLeaf
	 * 		the leaf to that will have its value modified
	 * @return a leaf with a value that is safe to modify
	 */
	@SuppressWarnings("unchecked")
	public FCMLeaf<K, V> getForModify(
			final FCMLeaf<K, V> originalLeaf,
			final Consumer<FCMLeaf<K, V>> updateCache) {

		final MerkleNode[] path = replacePath(getRoot(), originalLeaf.getRoute(), 0);

		// Path replacement currently DOES NOT fast copy internal nodes, it uses the constructable
		// registry to make a pseudo-fast-copy. FCMLeaf is actually an internal node and not a leaf,
		// so it won't have been fast copied. This means we must manually fast copy the value.
		final FCMLeaf<K, V> newLeaf = path[path.length - 1].cast();
		final V originalValue = newLeaf.getValue();
		if (originalValue.getReferenceCount() > 1) {
			newLeaf.setChild(1, newLeaf.getValue().copy(), newLeaf.getValue().getRoute());
		}

		updateCache.accept(newLeaf);

		if (originalLeaf == rightMostLeaf) {
			rightMostLeaf = newLeaf;
		}

		return newLeaf;
	}

	/**
	 * This method takes O(n) where n is the number of nodes (leaves and internal nodes).
	 * <p>This method should only be used for testing to guarantee correctness of the
	 * mutation methods.</p>
	 *
	 * @return Information about the balance of the tree
	 */
	public BalanceInfo getBalanceInfo() {
		return this.getRoot().getBalanceInfo();
	}

	/**
	 * Insert a leaf into a tree that currently has no leaves.
	 *
	 * @param leaf
	 * 		the leaf to insert
	 */
	private void insertIntoEmptyTree(final FCMLeaf<K, V> leaf) {
		getRoot().setLeftChild(leaf);
		rightMostLeaf = leaf;
	}

	/**
	 * Insert a leaf into a tree that currently has only one leaf
	 *
	 * @param leaf
	 * 		the leaf to insert
	 */
	private void insertIntoTreeWithOneLeaf(final FCMLeaf<K, V> leaf) {
		getRoot().setRightChild(leaf);
		rightMostLeaf = leaf;
	}

	/**
	 * Inserts a leaf into the tree.
	 *
	 * @param leaf
	 * 		New leaf to be inserted into the tree
	 * @param updateCache
	 * 		a function that is used to register changes that may need to be tracked by a cache in the outer scope
	 */
	@SuppressWarnings("unchecked")
	public void insert(final FCMLeaf<K, V> leaf, final Consumer<FCMLeaf<K, V>> updateCache) {
		throwIfImmutable();

		if (getSize().getValue() == 0) {
			this.insertIntoEmptyTree(leaf);
		} else if (getSize().getValue() == 1) {
			this.insertIntoTreeWithOneLeaf(leaf);
		} else {

			final FCMLeaf<K, V> nodeToPush = this.findFirstLeafAtCompleteSubtree(
					BitUtil.findLeftMostBit(getSize().getValue()),
					getSize().getValue(),
					getRoot()).cast();

			// Take an artificial reference to the node to push down to prevent it from being released too soon.
			nodeToPush.incrementReferenceCount();

			// Replace path down to the parent of the node to push down.
			final MerkleNode[] path = replacePath(getRoot(), nodeToPush.getRoute(), 1);

			final FCMInternalNode<K, V> parentOfNodeToPush = path[path.length - 2].cast();
			final int childIndexOfNodeToPush = findChildPositionInParent(parentOfNodeToPush, nodeToPush);

			// Add a new internal node
			final FCMInternalNode<K, V> newParent = new FCMInternalNode<>();
			parentOfNodeToPush.setChild(childIndexOfNodeToPush, newParent);

			// Copy the pushed-down node
			FCMLeaf<K, V> copy = copyTreeToLocation(newParent, 0, nodeToPush);
			updateCache.accept(copy);

			// Release the artificial reference now that the copy has been made
			nodeToPush.decrementReferenceCount();

			newParent.setRightChild(leaf);
			rightMostLeaf = leaf;
		}

		getSize().increment();
	}

	/**
	 * Walks node by node from the specified node to the first leaf in the last
	 * complete layer
	 *
	 * @param leftMostOneBit
	 * 		Left most bit
	 * @param size
	 * 		Number of leaves
	 * @param node
	 * 		Initial node to start path
	 * @return First leaf at complete subtree
	 */
	private FCMNode<K, V> findFirstLeafAtCompleteSubtree(long leftMostOneBit, final long size, FCMNode<K, V> node) {
		for (leftMostOneBit >>= 1; leftMostOneBit > 0; leftMostOneBit >>= 1) {
			node = (size & leftMostOneBit) == 0 ? node.getLeftChild() : node.getRightChild();
		}

		return node;
	}

	/**
	 * Returns the right most leaf of the tree.
	 * This method is available in order to test correctness
	 * of mutating methods.
	 *
	 * @return Right most leaf
	 */
	protected FCMLeaf<K, V> getRightMostLeaf() {
		return rightMostLeaf;
	}

	/**
	 * Returns the number of leaves in the tree.
	 *
	 * <p>This operation takes O(1) time</p>
	 *
	 * @return the number of leaves in the tree
	 */
	public long size() {
		return getSize().getValue();
	}

	public void setSize(final long size) {
		throwIfImmutable();
		setSize(new MerkleLong(size));
	}

	/**
	 * Returns {@code true} if this tree contains no leaves.
	 *
	 * <p>This operation takes O(1) time</p>
	 *
	 * @return {@code true} if this tree contains no leaves
	 */
	public boolean isEmpty() {
		return getSize().getValue() == 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("[size: %d]", getSize().getValue());
	}

	/**
	 * Returns an iterator over the leaves of the tree.
	 *
	 * @return Iterator over the leaves of the tree.
	 */
	public Iterator<FCMLeaf<K, V>> leafIterator() {
		return new MerkleFCMLeafIterator<>(this.getRoot());
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
	public void initialize() {
		this.setRightMostLeaf();
	}
}

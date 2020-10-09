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
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.common.merkle.utility.MerkleLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fast Copyable Merkle Tree
 *
 * @param <K>
 * 		Type for keys
 * @param <V>
 * 		Type for values
 */
public class FCMTree<K extends FCMKey, V extends FCMValue>
		extends AbstractMerkleInternal
		implements MerkleInternal, FastCopyable<FCMTree<K, V>> {

	public static final long CLASS_ID = 0x84bec080aa5cfeecL;

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * Delimiter to identify to the beginning of a serialized tree
	 */
	public static final int BEGIN_MARKER_VALUE = 1_835_364_971; // 'm', 'e', 'r', 'k'

	/**
	 * Delimiter to identify the end of a serialized tree
	 */
	public static final int END_MARKER_VALUE = 1_818_588_274; // 'l', 'e', 't', 'r'

	private static final Marker FCM_TREE = MarkerManager.getMarker("FCM_TREE");

	private static final Logger LOG = LogManager.getLogger(FCMTree.class);

	/**
	 * Deserializer for keys
	 */
	private final SerializedObjectProvider keyProvider;

	/**
	 * Deserializer for values
	 */
	private final SerializedObjectProvider valueProvider;

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
	 * Creates an instance of {@link FCMTree} that mustn't need the
	 * Key's and Value's SerializedObjectProviders.
	 * {@link SerializableHashable}
	 */
	public FCMTree() {
		this(null, null);
	}

	public FCMTree(final SerializedObjectProvider keyProvider, final SerializedObjectProvider valueProvider) {
		this(new MerkleLong(0), new FCMInternalNode<>(), keyProvider, valueProvider);
	}

	private FCMTree(final MerkleLong size,
			final FCMInternalNode<K, V> root,
			final SerializedObjectProvider keyProvider,
			final SerializedObjectProvider valueProvider) {
		super();
		setSize(size);
		setRoot(root);
		this.keyProvider = keyProvider;
		this.valueProvider = valueProvider;
	}

	private FCMTree(final FCMTree<K, V> fcmTreeSource) {
		super();
		this.setSize(fcmTreeSource.getSize().copy());
		this.setRoot(fcmTreeSource.getRoot().copy());
		this.rightMostLeaf = fcmTreeSource.rightMostLeaf;
		this.keyProvider = fcmTreeSource.keyProvider;
		this.valueProvider = fcmTreeSource.valueProvider;
		fcmTreeSource.rightMostLeaf = null;
		setImmutable(false);
		fcmTreeSource.setImmutable(true);
	}

	public FCMTree<K, V> copy() {
		throwIfImmutable();
		throwIfReleased();
		return new FCMTree<>(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFrom(SerializableDataInputStream inStream) throws IOException {
		final int beginMarker = inStream.readInt();
		if (BEGIN_MARKER_VALUE != beginMarker) {
			throw new IOException("The stream is not at the beginning of a serialized FCMap");
		}

		deserializeRootHash(inStream); // discards root hash
		final int endMarker = inStream.readInt();
		if (END_MARKER_VALUE != endMarker) {
			throw new IOException("The serialized FCMap stream ends unexpectedly");
		}
	}

	public List<FCMLeaf<K, V>> copyTreeFrom(SerializableDataInputStream inputStream) throws IOException {
		try {
			// Discard the version number
			inputStream.readLong();
			final List<FCMLeaf<K, V>> leaves = deserializeLeaves(inputStream);
			setSize(new MerkleLong(leaves.size()));
			if (getSize().getValue() == 0) {
				setRoot(new FCMInternalNode<>());
				return leaves;
			}

			List<? extends FCMNode<K, V>> internalNodes = this.generateInitialInternalNodes(leaves);
			while (internalNodes.size() > 1) {
				internalNodes = this.generateInternalLevel(internalNodes);
			}

			setRoot((FCMInternalNode<K, V>) internalNodes.get(0));
			this.setRightMostLeaf();
			return leaves;
		} catch (IOException ioException) {
			throw ioException;
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private List<FCMLeaf<K, V>> deserializeLeaves(final SerializableDataInputStream inputStream) throws IOException {
		return new FCSerializer<K, V>().deserialize(this.keyProvider, this.valueProvider, inputStream);
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
	 */
	public void delete(final FCMLeaf<K, V> leafToDelete) {
		throwIfImmutable();
		if (leafToDelete == null) {
			throw new NullPointerException("Delete does not support null leaves");
		}

		if (getSize().getValue() < 3) {
			this.deleteLeafFromBasicTree(leafToDelete);
			return;
		}

		this.deleteLeaf(leafToDelete);
		getSize().decrement();
		this.setRightMostLeaf();
	}

	/**
	 * Deletes a leaf.
	 *
	 * @param leafToDelete
	 * 		Leaf to delete from the tree
	 */
	private void deleteLeaf(final FCMLeaf<K, V> leafToDelete) {
		if (this.rightMostLeaf.equals(leafToDelete)) {
			// Leaf deleted is right most leaf. Its sibling modifies its parent
			final FCMInternalNode<K, V> parentOfLeafToDelete = leafToDelete.getParent();
			final FCMLeaf<K, V> leftLeaf = (FCMLeaf<K, V>) parentOfLeafToDelete.getLeftChild();
			this.setReplacementPath(parentOfLeafToDelete, leftLeaf);
		} else {
			final FCMInternalNode<K, V> rightMostParent = this.rightMostLeaf.getParent();
			final FCMInternalNode<K, V> parent = leafToDelete.getParent();
			if (rightMostParent.equals(parent)) {
				// Leaf deleted is sibling of right most leaf
				this.setReplacementPath(parent, this.rightMostLeaf);
			} else {
				this.setReplacementPath(leafToDelete, this.rightMostLeaf);
				final FCMLeaf<K, V> newLeaf = (FCMLeaf<K, V>) rightMostParent.getLeftChild();
				this.setReplacementPath(rightMostParent, newLeaf);
			}
		}
	}

	/**
	 * Deletes a leaf from a basic tree.
	 *
	 * @param leafToDelete
	 * 		Leaf to delete from the tree
	 */
	private void deleteLeafFromBasicTree(final FCMLeaf<K, V> leafToDelete) {
		if (getSize().getValue() < 1) {
			throw new IllegalStateException("The tree is empty. No leaf to delete");
		} else if (getSize().getValue() == 1) {
			this.setRoot(new FCMInternalNode<>());
			this.rightMostLeaf = null;
		} else if (getSize().getValue() == 2) {
			final FCMInternalNode<K, V> oldRoot = this.getRoot();
			if (this.rightMostLeaf.equals(leafToDelete)) {
				this.rightMostLeaf = (FCMLeaf<K, V>) oldRoot.getLeftChild();
				this.setRoot(oldRoot.createNewNodeWithOnlyLeftChild());
			} else {
				this.setRoot(oldRoot.createNewNodeWithRightChildAsLeftChild());
			}
		}

		getSize().decrement();
	}

	/**
	 * After a deletion is performed, the right most leaf changed, and must be set again.
	 */
	private void setRightMostLeaf() {
		if (getSize().getValue() < 3) {
			final FCMInternalNode<K, V> root = this.getRoot();
			final FCMNode<K, V> child = root.getRightChild() != null ? root.getRightChild() : root.getLeftChild();
			this.rightMostLeaf = (FCMLeaf<K, V>) child;
		} else {
			final long leftMostOneBit = BitUtil.findLeftMostBit(getSize().getValue() - 1);
			FCMNode<K, V> node = this.findFirstLeafAtCompleteSubtree(leftMostOneBit, getSize().getValue() - 1,
					this.getRoot());
			this.rightMostLeaf = (FCMLeaf<K, V>) node.getRightChild();
		}
	}

	/**
	 * Creates and sets the replacement path up to the root for the specified new node.
	 *
	 * @param oldNode
	 * 		Node that is going to be removed from the path
	 * @param newNode
	 * 		New node that is going to take place of the old node in the replaced path
	 */
	private void setReplacementPath(final FCMNode<K, V> oldNode, final FCMNode<K, V> newNode) {
		final FCMInternalNode<K, V> oldRoot = this.getRoot();
		final FCMInternalNode<K, V> oldParent = oldNode.getParent();
		final FCMInternalNode<K, V> newParent = oldParent.createNewNodeWithChildReplaced(oldNode, newNode);
		final FCMInternalNode<K, V> newRoot;
		if (oldParent.equals(oldRoot)) {
			newRoot = newParent;
		} else {
			newRoot = this.createReplacementPath(oldParent, newParent);
		}

		this.setRoot(newRoot);
	}

	/**
	 * Replaces an old leaf with a new leaf.
	 *
	 * @param oldLeaf
	 * 		Old leaf to replace
	 * @param newLeaf
	 * 		New leaf replacing
	 */
	public void update(final FCMLeaf<K, V> oldLeaf, final FCMLeaf<K, V> newLeaf) {
		throwIfImmutable();
		if (oldLeaf == null || newLeaf == null) {
			throw new NullPointerException("Update does not support null leaves");
		} else if (getSize().getValue() < 1) {
			throw new IllegalStateException("The tree is empty. No leaf to replace");
		}

		if (this.rightMostLeaf.equals(oldLeaf)) {
			this.rightMostLeaf = newLeaf;
		}

		this.setReplacementPath(oldLeaf, newLeaf);
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
	 * Inserts a leaf into the tree.
	 *
	 * @param leaf
	 * 		New leaf to be inserted into the tree
	 */
	public void insert(final FCMLeaf<K, V> leaf) {
		throwIfImmutable();
		final FCMInternalNode<K, V> localRoot = this.getRoot();
		if (getSize().getValue() == 0) {
			this.insertIntoEmptyTree(leaf);
			return;
		} else if (getSize().getValue() == 1) {
			this.insertIntoTreeWithOneLeaf(localRoot, leaf);
			return;
		}

		final long leftMostOneBit = BitUtil.findLeftMostBit(getSize().getValue());
		final FCMNode<K, V> node = this.findFirstLeafAtCompleteSubtree(leftMostOneBit, getSize().getValue(), localRoot);

		// replace the leaf node with a new node name newNode
		final FCMInternalNode<K, V> newNode = new FCMInternalNode<>();
		newNode.setRightChild(leaf);

		final FCMInternalNode<K, V> newRoot = this.createReplacementPath(node, newNode);
		newNode.setLeftChild(node);

		this.setRoot(newRoot);

		setLatestLeafAsRightMostLeaf(leaf);
		getSize().increment();
	}

	private void setLatestLeafAsRightMostLeaf(final FCMLeaf<K, V> leaf) {
		this.rightMostLeaf = leaf;
	}

	private void insertIntoTreeWithOneLeaf(final FCMInternalNode<K, V> oldRoot, final FCMLeaf<K, V> leaf) {
		final FCMInternalNode<K, V> newRoot = new FCMInternalNode<>();
		final FCMNode<K, V> leftChild = oldRoot.getLeftChild();
		newRoot.setLeftChild(leftChild);
		leftChild.setParent(newRoot);

		newRoot.setRightChild(leaf);
		leaf.setParent(newRoot);

		this.setRoot(newRoot);
		this.rightMostLeaf = leaf;
		getSize().increment();
	}

	private void insertIntoEmptyTree(final FCMLeaf<K, V> leaf) {
		final FCMInternalNode<K, V> newRoot = new FCMInternalNode<>();
		newRoot.setLeftChild(leaf);
		leaf.setParent(newRoot);
		this.setRoot(newRoot);
		this.rightMostLeaf = leaf;
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

	private FCMInternalNode<K, V> createReplacementPath(FCMNode<K, V> node, FCMInternalNode<K, V> newNode) {
		FCMInternalNode<K, V> oldParent = node.getParent();
		while (oldParent != null) {
			FCMInternalNode<K, V> newParent = oldParent.createNewNodeWithChildReplaced(node, newNode);
			node = oldParent;
			newNode = newParent;
			oldParent = oldParent.getParent();
		}

		return newNode;
	}

	/**
	 * Returns the right most leaf of the tree.
	 * This method is available in order to test correctness
	 * of mutating methods.
	 *
	 * @return Right most leaf
	 */
	protected FCMLeaf<K, V> getRightMostLeaf() {
		return this.rightMostLeaf;
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
	 * Returns an iterator over the nodes of the tree
	 * as a Breadth first search.
	 *
	 * @return Iterator over the nodes of the tree
	 */
	@Deprecated
	public Iterator<FCMNode<K, V>> nodeIterator() {
		return new MerkleFCMNodeIterator<>(this.getRoot());
	}

	/**
	 * Generates the parents of the leaves.
	 *
	 * @param leaves
	 * 		Leaves from the tree to build
	 * @return List of parent nodes of the provided leaves
	 */
	private List<? extends FCMNode<K, V>> generateInitialInternalNodes(final List<FCMLeaf<K, V>> leaves) {
		final long sizeofLastCompleteLevel = BitUtil.findLeftMostBit(leaves.size());
		final long nextSizeOfLastCompleteLevel = sizeofLastCompleteLevel << 1;
		final long numberOfLeavesInUpperLevel = nextSizeOfLastCompleteLevel - leaves.size();
		final long numberOfLeavesAtTheBottom = leaves.size() - numberOfLeavesInUpperLevel;
		if (numberOfLeavesAtTheBottom < 1) {
			return this.generateInternalLevel(leaves);
		}

		final List<? extends FCMNode<K, V>> bottomLeaves = leaves.subList(0, (int) numberOfLeavesAtTheBottom);
		final List<? extends FCMNode<K, V>> upperLeaves = leaves.subList((int) numberOfLeavesAtTheBottom,
				leaves.size());
		final List<FCMNode<K, V>> internalNodes = new ArrayList<>(generateInternalLevel(bottomLeaves));
		internalNodes.addAll(upperLeaves);
		return internalNodes;
	}

	/**
	 * Generates the complete tree based on the provided nodes.
	 *
	 * @param nodes
	 * 		Parent nodes
	 * @return Parent nodes of the provided parent nodes
	 */
	private List<FCMInternalNode<K, V>> generateInternalLevel(List<? extends FCMNode<K, V>> nodes) {
		List<FCMInternalNode<K, V>> internalNodes = new ArrayList<>(nodes.size() / 2);

		if (nodes.size() == 1) {
			FCMInternalNode<K, V> internalNode = new FCMInternalNode<>();
			final FCMNode<K, V> leftChild = nodes.get(0);
			internalNode.setLeftChild(leftChild);
			leftChild.setParent(internalNode);
			internalNodes.add(internalNode);
			return internalNodes;
		}

		final int limit = nodes.size();
		int index = 0;
		while (index < limit) {
			FCMInternalNode<K, V> internalNode = new FCMInternalNode<>();
			FCMNode<K, V> leftChild = nodes.get(index++);
			FCMNode<K, V> rightChild = nodes.get(index++);

			internalNode.setLeftChild(leftChild);
			internalNode.setRightChild(rightChild);

			leftChild.setParent(internalNode);
			rightChild.setParent(internalNode);

			internalNodes.add(internalNode);
		}

		return internalNodes;
	}


	public static byte[] deserializeRootHash(final SerializableDataInputStream inStream) throws IOException {
		final byte[] rootHash = new byte[DigestType.SHA_384.digestLength()];
		inStream.readFully(rootHash, 0, DigestType.SHA_384.digestLength());
		return rootHash;
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
	public void initialize(final MerkleInternal oldNode) {
		this.setRightMostLeaf();
	}
}

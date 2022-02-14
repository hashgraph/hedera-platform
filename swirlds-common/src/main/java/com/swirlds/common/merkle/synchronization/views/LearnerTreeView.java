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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;

import java.io.IOException;

/**
 * A "view" into a merkle tree (or subtree) used to perform a reconnect operation. This view is used to access
 * the tree by the learner.
 *
 * @param <T>
 * 		the type of an object which signifies a merkle node (T may or may not actually be a MerkleNode type)
 */
public interface LearnerTreeView<T>
		extends LearnerExpectedLessonQueue<T>, LearnerInitializer<T>, TreeView<T> {

	/**
	 * Check if this view represents the root of the state.
	 *
	 * @return true if this view represents the root of the state
	 */
	boolean isRootOfState();

	/**
	 * Get the root of the tree (or subtree).
	 *
	 * @return the root
	 */
	T getOriginalRoot();

	/**
	 * Set the child of an internal node.
	 *
	 * @param parent
	 * 		the parent that will hold the child, may be null if the view allows null to represent
	 * 		internal nodes in the subtree (although it seems unlikely that such a representation would
	 * 		be very useful for views to use)
	 * @param childIndex
	 * 		the position of the child
	 * @param child
	 * 		the child, may be null if the view allows null to represent merkle leaf nodes in the subtree
	 * @throws MerkleSynchronizationException
	 * 		if the parent is not an internal node
	 */
	void setChild(T parent, int childIndex, T child);

	/**
	 * Get the child of a node.
	 *
	 * @param parent
	 * 		the parent in question
	 * @param childIndex
	 * 		the index of the child
	 * @return the child at the index
	 * @throws MerkleSynchronizationException
	 * 		if the parent is a leaf or if the child index is invalid
	 */
	T getChild(T parent, int childIndex);

	/**
	 * Get the hash of a node. If this view represents a tree that has null nodes within it, those nodes should cause
	 * this method to return a {@link Cryptography#getNullHash() null hash}.
	 *
	 * @param node
	 * 		the node
	 * @return the hash of the node
	 */
	Hash getNodeHash(T node);

	/**
	 * Convert a merkle node that is the root of a subtree with a custom merkle view
	 * to the type used by this view.
	 *
	 * @param node
	 * 		the root of the tree or the root of a custom view subtree
	 * @return the same nude but with the type used by this view
	 */
	T convertMerkleRootToViewType(MerkleNode node);

	/**
	 * Read a merkle leaf from the stream (as written by
	 * {@link TeacherTreeView#serializeLeaf(SerializableDataOutputStream, Object)}).
	 *
	 * @param in
	 * 		the input stream
	 * @return the leaf
	 * @throws IOException
	 * 		if a problem is encountered with the stream
	 */
	T deserializeLeaf(SerializableDataInputStream in) throws IOException;

	/**
	 * Read a merkle internal from the stream (as written by
	 * {@link TeacherTreeView#serializeInternal(SerializableDataOutputStream, Object)}).
	 *
	 * @param in
	 * 		the input stream
	 * @return the internal node
	 * @throws IOException
	 * 		if a problem is encountered with the stream
	 */
	T deserializeInternal(SerializableDataInputStream in) throws IOException;

	/**
	 * Release a leaf node.
	 *
	 * @param node
	 * 		the node to release
	 */
	void releaseNode(T node);
}

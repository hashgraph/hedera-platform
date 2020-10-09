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

package com.swirlds.common.merkle;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;

import java.util.List;

/**
 * <p>A MerkleInternal is one interior node in a Merkle tree structure.
 * It has the following properties:
 *     <ul>
 *         <li>It only has children (no data)</li>
 *         <li>Each child is a MerkleNode object</li>
 *         <li>Requires a no-arg constructor (due to inheritance from RuntimeConstructable)</li>
 *         <li>Requires a constructor that takes {@code List<Merkle> children} </li>
 *     </ul>
 *
 *
 * A MerkleInternal node may have utility methods that increase the number of children after the node has been
 * constructed. Child nodes MUST NOT be generated or modified in the following functions:
 *
 * - The zero-argument constructor
 * - getNumberOfChildren()
 * - getChild()
 * - isLeaf()
 *
 * It is highly recommended that any class implementing this interface extend {@link AbstractMerkleInternal}.
 */
public interface MerkleInternal extends MerkleNode {

	/**
	 * The maximum number of children that a MerkleInternal node can have
	 */
	int MAX_CHILD_COUNT = 64;

	/**
	 * Returns the number of all immediate children that are a MerkleNode object.
	 *
	 * This function must not change the number of children or modify any children in any way.
	 *
	 * @return number of children
	 */
	int getNumberOfChildren();

	/**
	 * Returns deterministically the child at position <i>index</i>
	 *
	 * This function must not change the number of children or modify any children in any way.
	 *
	 * @param index
	 * 		The position to look for the child.
	 * @param <T>
	 *           the type of the child
	 */
	<T extends MerkleNode> T getChild(final int index);

	/**
	 * Set the child at a particular index.
	 *
	 * This method responsible for updating the reference count of the new child and the previous child
	 * (if a child gets replaced).
	 *
	 * @param index
	 * 		The position where the child will be inserted.
	 * @param child
	 * 		A MerkleNode object.
	 */
	void setChild(final int index, final MerkleNode child);

	/**
	 * The minimum number of children that this node may have.
	 *
	 * @param version
	 * 		The version in question.
	 * @return The minimum number of children at the specified version.
	 */
	default int getMinimumChildCount(final int version) {
		return 0;
	}

	/**
	 * The maximum number of children that this node may have.
	 *
	 * The value returned must not be greater than MAX_CHILD_COUNT.
	 * If nodes violate this then reconnect might break.
	 *
	 * @param version
	 * 		The version in question.
	 * @return The maximum number of children at the specified version.
	 */
	default int getMaximumChildCount(final int version) {
		return MAX_CHILD_COUNT;
	}

	/**
	 * Check if the given class ID is valid for a particular child.
	 *
	 * @param index
	 * 		The child index.
	 * @param childClassId
	 * 		The class id of the child. May be NULL_CLASS_ID if the child is null.
	 * @param version
	 * 		The version in question.
	 * @return true if the child has the appropriate type for the given version.
	 */
	default boolean childHasExpectedType(final int index, final long childClassId, final int version) {
		return true;
	}

	/**
	 * Adds children that have been deserialized from a stream.
	 *
	 * @param children
	 * 		A list of children.
	 * @param version
	 * 		The version of the node when these children were serialized.
	 */
	default void addDeserializedChildren(List<MerkleNode> children, final int version) {
		for (int childIndex = 0; childIndex < children.size(); childIndex++) {
			setChild(childIndex, children.get(childIndex));
		}
	}

	/**
	 * This method is called after this node's children and all its descendents have been constructed and initialized.
	 * Nodes that maintain metadata derived from descendant nodes may build that metadata here.
	 *
	 * @param oldNode
	 * 		If this merkleNode was constructed by mutating a node of the same type, pass a reference to that
	 * 		node here. May be null. If not null, metadata from the old node may be used to construct metadata
	 * 		for this node more efficiently.
	 */
	default void initialize(MerkleInternal oldNode) {

	}

	/**
	 * Classes that implement MerkleInternal should not override this method.
	 *
	 * {@inheritDoc}
	 */
	@Override
	default boolean isLeaf() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * A MerkleInternal MUST not compute its own hash. The hash returned by this function must
	 * be the same as the hash most recently set by setHash, or null if no hash has yet been set.
	 */
	@Override
	Hash getHash();
}

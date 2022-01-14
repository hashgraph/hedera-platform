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

package com.swirlds.common.merkle;

import com.swirlds.common.MutabilityException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.utility.AbstractBinaryMerkleInternal;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;

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
 * It is highly recommended that any class implementing this interface extend either
 * {@link AbstractBinaryMerkleInternal} (if the node has 2 or fewer children) or {@link AbstractNaryMerkleInternal}
 * (if the node has greater than 2 children).
 */
public interface MerkleInternal extends MerkleNode {

	/**
	 * The maximum number of children that a MerkleInternal node can have
	 */
	int MAX_CHILD_COUNT_UBOUND = 64;
	/**
	 * The maximum number of children that a MerkleInternal node can have
	 */
	int MAX_CHILD_COUNT_LBOUND = 1;
	/**
	 * The minimum number of children that a MerkleInternal node can have
	 */
	int MIN_CHILD_COUNT = 0;

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
	 * <p>
	 * This function must not change the number of children or modify any children in any way.
	 * <p>
	 * If a child at an index that does not violate {@link #getMaximumChildCount(int)} is requested
	 * but that child has never been set, then null should be returned.
	 *
	 * @param index
	 * 		The position to look for the child.
	 * @param <T>
	 * 		the type of the child
	 * @return the child at the position <i>index</i>
	 * @throws com.swirlds.common.merkle.exceptions.IllegalChildIndexException if the index is negative or if
	 * the index is greater than {@link #getMaximumChildCount(int)}.
	 */
	<T extends MerkleNode> T getChild(final int index);

	/**
	 * Set the child at a particular index.
	 *
	 * Additionally, method is expected to perform the following operations:
	 * - invalidating this node's hash
	 * - decrementing the reference count of the displaced child (if any)
	 * - incrementing the reference count of the new child (if not null)
	 * - updating the route of the new child (if not null)
	 *
	 * @param index
	 * 		The position where the child will be inserted.
	 * @param child
	 * 		A MerkleNode object.
	 * @throws MutabilityException
	 * 		if the child is immutable
	 * @throws MerkleRouteException
	 * 		if the child's route is changed in an illegal manner
	 */
	void setChild(final int index, final MerkleNode child);

	/**
	 * Set the child at a particular index.
	 *
	 * Uses a precomputed route for the child. Useful for setting a child where the route is already known without
	 * paying the performance penalty of recreating the route.
	 *
	 * Additionally, method is expected to perform the following operations:
	 * - invalidating this node's hash
	 * - decrementing the reference count of the displaced child (if any)
	 * - incrementing the reference count of the new child (if not null)
	 * - updating the route of the new child (if not null)
	 *
	 * @param index
	 * 		The position where the child will be inserted.
	 * @param child
	 * 		A MerkleNode object.
	 * @param childRoute
	 * 		A precomputed route. No error checking, assumed to be correct.
	 * @throws MutabilityException
	 * 		if the child is immutable
	 * @throws com.swirlds.common.merkle.exceptions.MerkleRouteException
	 * 		if the child's route is changed in an illegal manner
	 */
	void setChild(final int index, final MerkleNode child, final MerkleRoute childRoute);

	/**
	 * The minimum number of children that this node may have.
	 *
	 * @param version
	 * 		The version in question.
	 * @return The minimum number of children at the specified version.
	 */
	default int getMinimumChildCount(final int version) {
		return MIN_CHILD_COUNT;
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
		return MAX_CHILD_COUNT_UBOUND;
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
	 * This method is called after this node's children and all its descendants have been constructed and initialized.
	 * Nodes that maintain metadata derived from descendant nodes may build that metadata here.
	 */
	default void initialize() {

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

	/**
	 * {@inheritDoc}
	 *
	 * When the route of a merkle internal is changed, this method must update the routes of all descendant nodes.
	 */
	@Override
	void setRoute(MerkleRoute route);

	/**
	 * {@inheritDoc}
	 *
	 * There are currently 3 strategies that internal nodes use to perform copy.
	 *
	 * 1) Cascading copy. When a node using this strategy is copied it simply calls copy on all of its children and
	 * adds those copies to the new object. This is inefficient -- a copy of a tree that uses cascading copy is an
	 * O(n) operation (where n is the number of nodes).
	 *
	 * 2) Smart copy (aka copy-on-write). When a node using this strategy is copied, it copies the root
	 * of its subtree. When a descendant is modified, it creates a path from the root down to the node
	 * and then modifies the copy of the node.
	 *
	 * 3) Self copy. When this internal node is copied it only copies metadata. Its children are uncopied, and the new
	 * node has null in place of any children. This strategy can only CURRENTLY be used on nodes where copying is
	 * managed by an ancestor node (for example, an ancestor that does smart copies).
	 *
	 * Eventually there will be utilities that manage all aspects of copying a merkle tree. At that point in time, all
	 * internal nodes will be required to implement copy strategy 3.
	 */
	@Override
	MerkleInternal copy();
}

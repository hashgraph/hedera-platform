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

import com.swirlds.common.Releasable;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.merkle.iterators.MerkleDepthFirstIterator;
import com.swirlds.common.merkle.iterators.MerkleInternalIterator;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A MerkleNode object has the following properties
 * <ul>
 *     <li>Doesn't need to compute its hash</li>
 *     <li>It's not aware of Cryptographic Modules</li>
 *     <li>Doesn't need to perform rsync</li>
 *     <li>Doesn't need to provide hints to the Crypto Module</li>
 * </ul>
 */
public interface MerkleNode extends Releasable, Hashable, SerializableDet {

	/**
	 * Check if this node is a leaf node.
	 *
	 * @return true if this is a leaf node in a merkle tree.
	 */
	boolean isLeaf();

//	/**
//	 * IMPORTANT: this method is a place holder -- routes are not yet fully enabled.
//	 *
//	 * Get the (encoded) route from the root of the tree down to this node.
//	 *
//	 * To read or manipulate the route returned, first wrap it in a {@link MerkleRoute} object.
//	 *
//	 * Returns the value specified by setPath().
//	 *
//	 * @return A MerkleRoute object.
//	 */
//	int[] getRoute();
//
//	/**
//	 * IMPORTANT: this method is a place holder -- routes are not yet fully enabled.
//	 *
//	 * This method is used to store the route from the root to this node.
//	 *
//	 * It is expected that the value set by this method be stored and returned by getPath().
//	 */
//	void setRoute(int[] route);

	/**
	 * This method should be called every time this node is added as the child of another node.
	 *
	 * The reference count of a node corresponds to the number of parent nodes which reference the node.
	 * All nodes in a tree should have a reference count of at least 1 with the exception of the root
	 * (which will have a reference count of 0).
	 */
	void incrementReferenceCount();

	/**
	 * This method should be called every time this node is removed as the child of another node. If the
	 * reference count drops to 0, this method is responsible for deleting external data held by the node
	 * and for decrementing the reference count of children (if this node has children).
	 */
	void decrementReferenceCount();

	/**
	 * @return the current reference count for this node. When newly created a node should have a reference count
	 * of exactly 0.
	 */
	int getReferenceCount();

	/**
	 * Execute a function on each non-null node in the subtree rooted at this node (which includes this node).
	 *
	 * @param operation
	 * 		The function to execute.
	 */
	default void forEachNode(Consumer<MerkleNode> operation) {
		forEachNode(MerkleDepthFirstIterator::new, operation);
	}

	/**
	 * Execute a function on each non-null node returned by an iterator that is walking over the the subtree rooted
	 * at this node (which includes this node).
	 *
	 * @param iteratorFactory
	 * 		A method that returns an iterator.
	 * @param operation
	 * 		A method to call on each node returned by the iterator.
	 * @param <T>
	 * 		The type of the node returned by the iterator.
	 */
	default <T extends MerkleNode> void forEachNode(Function<MerkleNode, Iterator<T>> iteratorFactory,
			Consumer<T> operation) {
		Iterator<T> iterator = iteratorFactory.apply(this);
		while (iterator.hasNext()) {
			T next = iterator.next();
			if (next != null) {
				operation.accept(next);
			}
		}
	}

	/**
	 * Utility method for initializing an entire tree.
	 */
	default void initializeTree() {
		forEachNode(MerkleInternalIterator::new, (node) -> node.initialize(null));
	}
}

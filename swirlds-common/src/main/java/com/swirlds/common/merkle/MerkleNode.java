/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.merkle;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.merkle.exceptions.MerkleRouteException;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.merkle.route.MerkleRouteIterator;
import com.swirlds.common.merkle.synchronization.views.CustomReconnectRoot;
import com.swirlds.common.merkle.utility.MerkleSerializationStrategy;

import java.util.Set;
import java.util.function.Consumer;

/**
 * A MerkleNode object has the following properties
 * <ul>
 *     <li>Doesn't need to compute its hash</li>
 *     <li>It's not aware of Cryptographic Modules</li>
 *     <li>Doesn't need to perform rsync</li>
 *     <li>Doesn't need to provide hints to the Crypto Module</li>
 * </ul>
 */
public interface MerkleNode extends FastCopyable, Hashable, SerializableDet {

	/**
	 * Check if this node is a leaf node. As fast or faster than using instanceof.
	 *
	 * @return true if this is a leaf node in a merkle tree.
	 */
	boolean isLeaf();

	/**
	 * Check if this node is a {@link MerkleInternal} node.
	 *
	 * @return true if this is an internal node
	 */
	default boolean isInternal() {
		return !isLeaf();
	}

	/**
	 * Return true if this node is the root of a subtree that has a custom view for reconnect. Nodes that return
	 * true must implement the interface {@link CustomReconnectRoot}.
	 *
	 * @return true if the node has a custom view to be used during reconnect
	 */
	default boolean hasCustomReconnectView() {
		return false;
	}

	/**
	 * Get the types of serialization supported by a given version of this class.
	 *
	 * @param version
	 * 		the version in question
	 * @return a set of supported strategies
	 */
	Set<MerkleSerializationStrategy> supportedSerialization(int version);

	/**
	 * Blindly cast this merkle node into a leaf node, will fail if node is not actually a leaf node.
	 */
	default MerkleLeaf asLeaf() {
		return cast();
	}

	/**
	 * Blindly cast this merkle node into an internal node, will fail if node is not actually an internal node.
	 */
	default MerkleInternal asInternal() {
		return cast();
	}

	/**
	 * Blindly cast this merkle node into the given type, will fail if node is not actually that type.
	 *
	 * @param <T>
	 * 		this node will be cast into this type
	 */
	@SuppressWarnings("unchecked")
	default <T extends MerkleNode> T cast() {
		return (T) this;
	}

	/**
	 * Returns the value specified by setRoute(), i.e. the route from the root of the tree down to this node.
	 *
	 * If setRoute() has not yet been called, this method should return an empty merkle route.
	 */
	MerkleRoute getRoute();

	/**
	 * This method is used to store the route from the root to this node.
	 *
	 * It is expected that the value set by this method be stored and returned by getPath().
	 *
	 * This method should NEVER be called manually. Only merkle utility code in AbstractMerkleInternal
	 * should ever call this method.
	 *
	 * @throws MerkleRouteException
	 * 		if this node has a reference count is not exactly 1. Routes may only be changed
	 * 		when a node is first added as the child of another node or if there is a single parent
	 * 		and the route of that parent changes.
	 */
	void setRoute(final MerkleRoute route);

	/**
	 * Get the node that is reached by starting at this node and traversing along a provided route.
	 *
	 * @param route
	 * 		the route to follow
	 * @return the node at the end of the route
	 */
	default MerkleNode getNodeAtRoute(final MerkleRoute route) {
		return new MerkleRouteIterator(this, route).getLast();
	}

	/**
	 * Get the node that is reached by starting at this node and traversing along a provided route.
	 *
	 * @param steps
	 * 		the steps in the route
	 * @return the node at the end of the route
	 */
	default MerkleNode getNodeAtRoute(final int... steps) {
		return getNodeAtRoute(MerkleRouteFactory.buildRoute(steps));
	}

	/**
	 * This method should be called every time this node is added as the child of another node. Increases the
	 * reference count by 1.
	 *
	 * The reference count of a node corresponds to the number of parent nodes which reference the node.
	 * All nodes in a tree should have a reference count of at least 1 with the exception of the root
	 * (which will have a reference count of 0).
	 *
	 * @throws com.swirlds.common.exceptions.ReferenceCountException
	 * 		if this node has already been released
	 */
	void incrementReferenceCount();

	/**
	 * This method should be called every time this node is removed as the child of another node.
	 * <p>
	 * If the reference count drops to 0, this method is responsible for deleting external data held by the node
	 * and for decrementing the reference count of children (if this node has children).
	 *
	 * @throws com.swirlds.common.exceptions.ReferenceCountException
	 * 		if the reference count is 0 or of the node has already been released
	 */
	void decrementReferenceCount();

	/**
	 * Get the reference count for this node.
	 * <p>
	 * When initially constructed all nodes must have a reference count of 0. A node in this state is considered to
	 * have an implicit reference.
	 * <p>
	 * If the reference count is greater than 1 it is considered to have explicit references. If a node has a
	 * reference count of 0 and transitions to a reference count of 1 then its implicit reference is "dropped" and the
	 * reference becomes explicit. Once a node has an explicit reference it can never again hold an implicit reference.
	 * <p>
	 * When a node is finally released (either by a reference count dropping from 1 to 0 or by the {@link #release()}
	 * method being called), its reference count is set to -1. Once the reference count has been set to -1 it must never
	 * be allowed to change again.
	 *
	 * @return the current reference count for this node
	 */
	int getReferenceCount();

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	MerkleNode copy();

	/**
	 * Create a pre-ordered depth first iterator for this tree (or subtree).
	 *
	 * @return a configurable iterator
	 */
	default <T extends MerkleNode> MerkleIterator<T> treeIterator() {
		return new MerkleIterator<>(this);
	}

	/**
	 * Execute a function on each non-null node in the subtree rooted at this node (which includes this node).
	 *
	 * @param operation
	 * 		The function to execute.
	 */
	default void forEachNode(final Consumer<MerkleNode> operation) {
		treeIterator().forEachRemaining(operation);
	}
}

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

package com.swirlds.common.merkle.copy;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.function.Predicate;

import static com.swirlds.common.merkle.utility.MerkleSerializationStrategy.EXTERNAL_SELF_SERIALIZATION;
import static com.swirlds.common.merkle.utility.MerkleSerializationStrategy.SELF_SERIALIZATION;

/**
 * This class provides utility methods for initializing trees.
 */
public final class MerkleInitialize {

	private MerkleInitialize() {

	}

	/**
	 * This filter is used when iterating over a tree to do initialization after a deserialization.
	 */
	private static boolean deserializationFilter(final MerkleNode node) {
		// Leaf nodes are never initialized.
		// Internal nodes that are self serializing don't require initialization.
		return !node.isLeaf() && !node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION);
	}

	/**
	 * This filter is used when iterating over a tree to do initialization after an external deserialization.
	 */
	private static boolean externalDeserializationFilter(final MerkleNode node) {
		// Leaf nodes are never initialized.
		// Internal nodes that are self serializing don't require initialization.
		return !node.isLeaf() && !node.supportedSerialization(node.getVersion()).contains(SELF_SERIALIZATION) &&
				!node.supportedSerialization(node.getVersion()).contains(EXTERNAL_SELF_SERIALIZATION);
	}

	/**
	 * This filter is used when iterating over a tree to do initialization after a copy.
	 */
	private static boolean copyFilter(final MerkleNode node) {
		// Leaf nodes are never initialized.
		return !node.isLeaf();
	}

	/**
	 * Initialize the tree after deserialization.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterDeserialization(final MerkleNode root) {
		initializeWithFilter(root, MerkleInitialize::deserializationFilter);
	}

	/**
	 * Initialize the tree after external deserialization.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterExternalDeserialization(final MerkleNode root) {
		initializeWithFilter(root, MerkleInitialize::externalDeserializationFilter);
	}

	/**
	 * Initialize the tree after it has been copied.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterCopy(final MerkleNode root) {
		initializeWithFilter(root, MerkleInitialize::copyFilter);
	}

	private static void initializeWithFilter(final MerkleNode root, final Predicate<MerkleNode> filter) {

		if (root == null) {
			return;
		}

		// If a node should not be initialized, then neither should any of its descendants be initialized.
		final Predicate<MerkleInternal> descendantFilter = filter::test;

		root.treeIterator()
				.setFilter(filter)
				.setDescendantFilter(descendantFilter)
				.forEachRemaining(node -> ((MerkleInternal) node).initialize());
	}
}

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

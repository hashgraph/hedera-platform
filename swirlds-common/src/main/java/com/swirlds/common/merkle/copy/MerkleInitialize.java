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

package com.swirlds.common.merkle.copy;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;

import static com.swirlds.common.merkle.copy.InitializationType.COPY;
import static com.swirlds.common.merkle.copy.InitializationType.DESERIALIZATION;
import static com.swirlds.common.merkle.copy.InitializationType.EXTERNAL_DESERIALIZATION;
import static com.swirlds.common.merkle.copy.InitializationType.RECONNECT;

/**
 * This class provides utility methods for initializing trees.
 */
public final class MerkleInitialize {

	private MerkleInitialize() {

	}

	/**
	 * Initialize the tree after deserialization.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterDeserialization(final MerkleNode root) {
		initializeWithType(root, DESERIALIZATION);
	}

	/**
	 * Initialize the tree after external deserialization.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterExternalDeserialization(final MerkleNode root) {
		initializeWithType(root, EXTERNAL_DESERIALIZATION);
	}

	/**
	 * Initialize the tree after reconnect.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterReconnect(final MerkleNode root) {
		initializeWithType(root, RECONNECT);
	}

	/**
	 * Initialize the tree after it has been copied.
	 *
	 * @param root
	 * 		the tree (or subtree) to initialize
	 */
	public static void initializeTreeAfterCopy(final MerkleNode root) {
		initializeWithType(root, COPY);
	}

	private static void initializeWithType(final MerkleNode root, final InitializationType type) {
		final Iterator<MerkleInternal> iterator = new MerkleInitializationIterator(root, type);
		iterator.forEachRemaining(MerkleInternal::initialize);
	}
}

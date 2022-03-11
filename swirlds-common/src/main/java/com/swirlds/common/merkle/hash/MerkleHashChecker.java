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

package com.swirlds.common.merkle.hash;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleIterationOrder;
import com.swirlds.common.merkle.iterators.MerkleIterator;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.utility.ValueReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LoggingUtils.plural;

public final class MerkleHashChecker {

	private static final Logger LOGGER = LogManager.getLogger();

	private MerkleHashChecker() {
	}

	/**
	 * Traverses the merkle tree and checks if there are any hashes that are not valid. Recalculates all hashes
	 * that have been calculated externally and check them against the getHash value.
	 *
	 * @param root
	 * 		the root of the merkle tree
	 * @param mismatchCallback
	 * 		the method to call if a mismatch is found. May be called many times.
	 */
	public static void findInvalidHashes(
			final MerkleNode root,
			final Consumer<MerkleNode> mismatchCallback) {
		if (root == null) {
			return;
		}

		root.forEachNode(node -> findInvalidHash(node, mismatchCallback));
	}

	private static void findInvalidHash(final MerkleNode node,
			final Consumer<MerkleNode> mismatchCallback) {
		final Cryptography cryptography = CryptoFactory.getInstance();

		// some nodes calculate their own hash, we could potentially check these if we serialize and deserialize
		// them and then check their hash
		if (node == null || node.isSelfHashing()) {
			return;
		}

		final Hash old = node.getHash();
		if (old == null) {
			mismatchCallback.accept(node);
			return;
		}

		final Hash recalculated;
		if (node.isLeaf()) {
			recalculated = cryptography.digestSync(
					(SerializableHashable) node,
					Cryptography.DEFAULT_DIGEST_TYPE,
					false);
		} else {
			final MerkleInternal internal = node.asInternal();
			for (int childIndex = 0; childIndex < internal.getNumberOfChildren(); childIndex++) {
				final MerkleNode child = internal.getChild(childIndex);
				if (child != null && child.getHash() == null) {
					// It is impossible to compute the hash of a parent if the child has a null hash
					return;
				}
			}
			recalculated = cryptography.digestSync(internal, Cryptography.DEFAULT_DIGEST_TYPE, false);
		}

		if (!old.equals(recalculated)) {
			mismatchCallback.accept(node);
		}
	}

	/**
	 * Get a list of all nodes in a tree that have an invalid hash.
	 * Returns an empty list if the entire tree has valid hashes.
	 *
	 * @param root
	 * 		the root of the tree in question
	 * @return a list of nodes with invalid hashes (if there are any)
	 */
	public static List<MerkleNode> getNodesWithInvalidHashes(final MerkleNode root) {
		final List<MerkleNode> nodesWithInvalidHashes = new LinkedList<>();
		findInvalidHashes(root, nodesWithInvalidHashes::add);
		return nodesWithInvalidHashes;
	}

	/**
	 * Check if all of the hashes within a tree are valid.
	 * Write a detailed message to the log if invalid hashes are detected in the tree.
	 *
	 * @param root
	 * 		the root of the tree to check
	 * @param context
	 * 		the context that the check is being done in. This is written to the log if a problem is detected.
	 * @param limit
	 * 		the maximum number of invalid nodes to log. If the entire tree is invalid then the log could be massively
	 * 		spammed. A sane limit reduces the amount logged in that situation.
	 * @return true if the tree is valid, false if it is not valid
	 */
	public static boolean checkHashAndLog(final MerkleNode root, final String context, final int limit) {

		final List<MerkleNode> nodesWithInvalidHashes = getNodesWithInvalidHashes(root);

		if (nodesWithInvalidHashes.isEmpty()) {
			return true;
		}

		final StringBuilder sb = new StringBuilder();

		sb.append("Invalid merkle hashes detected in tree. Context = ").append(context).append("\n");


		int nodesWithNullHash = 0;
		int nodesWithInvalidHash = 0;

		for (final MerkleNode node : nodesWithInvalidHashes) {
			if (node.getHash() == null) {
				nodesWithNullHash++;
			} else {
				nodesWithInvalidHash++;
			}
		}

		sb.append(nodesWithNullHash).append(" ").append(plural(nodesWithNullHash, "node")).append(" ")
				.append(plural(nodesWithNullHash, "has a null hash", "have null hashes"))
				.append(". ");

		sb.append(nodesWithInvalidHash).append(" ").append(plural(nodesWithInvalidHash, "node")).append(" ")
				.append(plural(nodesWithInvalidHash, "has an invalid hash", "have invalid hashes"))
				.append(".\n");

		if (nodesWithInvalidHashes.size() > limit) {
			sb.append("Number of nodes exceeds maximum limit, only logging the first ")
					.append(limit).append(" ").append(plural(limit, "node")).append(".\n");
		}

		int count = 0;
		for (final MerkleNode node : nodesWithInvalidHashes) {
			count++;
			if (count > limit) {
				break;
			}

			sb.append("   - ").append(node.getClass().getSimpleName())
					.append(" @ ").append(node.getRoute()).append(" ");
			if (node.getHash() == null) {
				sb.append("has a null hash");
			} else {
				sb.append("has an invalid hash");
			}
			sb.append("\n");
		}

		LOGGER.error(EXCEPTION.getMarker(), sb);
		return false;
	}

	/**
	 * Generate a human-readable debug string that contains hash information about nodes near the top of a tree.
	 * No hash information is displayed for nodes below a certain depth, or for nodes below other nodes annotated
	 * with {@link com.swirlds.common.merkle.utility.DebugIterationEndpoint DebugIterationEndpoint}.
	 *
	 * @param root
	 * 		the root of a tree (or subtree) to iterate
	 * @param maxDepth
	 * 		the maximum depth of the tree to iterate
	 * @return a string containing debug information
	 */
	public static String generateHashDebugString(final MerkleNode root, final int maxDepth) {
		final StringBuilder sb = new StringBuilder();

		final Predicate<MerkleInternal> filter = (final MerkleInternal parent) -> !(
				parent.getClass().isAnnotationPresent(DebugIterationEndpoint.class)
						|| parent.getRoute().size() >= maxDepth);

		final Iterator<MerkleNode> iterator = new MerkleIterator<>(root)
				.setOrder(MerkleIterationOrder.PRE_ORDERED_DEPTH_FIRST)
				.setDescendantFilter(filter);

		iterator.forEachRemaining((final MerkleNode node) -> {

			// Indent
			final int depth = node.getRoute().size();
			for (int indent = 0; indent < depth; indent++) {
				sb.append("   ");
			}

			// Get child's index in its parent
			final String indexString;
			if (depth == 0) {
				// Root of the tree
				indexString = "(root)";
			} else {
				final ValueReference<Integer> step = new ValueReference<>(-1);
				node.getRoute().iterator().forEachRemaining(step::setValue);
				indexString = Integer.toString(step.getValue());
			}

			sb.append(indexString).append(" ")
					.append(node.getClass().getSimpleName()).append(" ")
					.append(node.getHash());

			if (iterator.hasNext()) {
				sb.append("\n");
			}
		});

		return sb.toString();
	}
}

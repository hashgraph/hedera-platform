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

package com.swirlds.common.merkle.utility;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class MerkleUtils {

	private MerkleUtils() {

	}

	/**
	 * Invalidate the hashes of an entire tree (or subtree) rooted at the given node. Does not perform invalidation
	 * for parts of the merkle tree that are self hashing.
	 *
	 * @param root
	 * 		the root of the tree (or subtree)
	 */
	public static void invalidateTree(final MerkleNode root) {
		final Iterator<MerkleNode> iterator = new MerkleInvalidationIterator(root);
		iterator.forEachRemaining(MerkleNode::invalidateHash);
	}

	/**
	 * Rehash the entire tree, discarding any hashes that are already computed.
	 * Does not rehash virtual parts of the tree that are self hashing.
	 *
	 * @param root
	 * 		the root of the tree to hash
	 * @return the computed hash of the {@code root} parameter or null if the parameter was null
	 */
	public static Hash rehashTree(final MerkleNode root) {
		if (root != null) {
			invalidateTree(root);
			final Future<Hash> future = CryptoFactory.getInstance().digestTreeAsync(root);
			try {
				return future.get();
			} catch (InterruptedException | ExecutionException e) {
				Thread.currentThread().interrupt();
			}
		}

		return null;
	}

	private static void buildMerkleString(
			final StringBuilder sb,
			final MerkleNode node,
			final int depth,
			final int indexInParent,
			final int parentNumberOfChildren,
			String indentation,
			final int maxDepth,
			final boolean printNodeDescription) {

		// Add indention
		if (parentNumberOfChildren > 0) {
			sb.append(indentation).append("  |");
			if (indexInParent < parentNumberOfChildren - 1) {
				indentation += "  |";
			} else {
				indentation += "   ";
			}
		}

		if (parentNumberOfChildren == 0) {
			sb.append("-(root) ");
		} else {
			sb.append("-(").append(indexInParent).append(") ");
		}

		if (node == null) {
			sb.append("null\n");
		} else {
			String[] classElements = node.getClass().toString().split("\\.");
			sb.append(classElements[classElements.length - 1]).append(
					": " + (printNodeDescription ? node.toString() : ""));

			if (node.isLeaf()) {
				sb.append(node.toString()).append("\n");
			} else {
				final MerkleInternal internal = node.cast();
				if (maxDepth > 0 && depth + 1 > maxDepth) {
					sb.append("...\n");
				} else {
					sb.append("\n");
					for (int childIndex = 0; childIndex < internal.getNumberOfChildren(); childIndex++) {
						buildMerkleString(sb, internal.getChild(childIndex), depth + 1, childIndex,
								internal.getNumberOfChildren(), indentation, maxDepth, printNodeDescription);
					}
				}
			}
		}
	}

	/**
	 * Get a string representing a merkle tree.
	 *
	 * @param root
	 * 		root of the merkle tree
	 * @return a string which represents this merkle tree
	 */
	public static String getMerkleString(final MerkleNode root) {
		return getMerkleString(root, false);
	}

	public static String getMerkleString(final MerkleNode root, final boolean printNodeDescription) {
		return getMerkleString(root, -1, printNodeDescription);
	}

	/**
	 * Get a string representing a merkle tree. Do not include nodes below a given depth.
	 *
	 * @param root
	 * 		root of the merke tree
	 * @param maxDepth
	 * 		max depth of nodes to be included in this string
	 * @param printNodeDescription
	 * 		whether print node description or not
	 * @return a string which represents this merkle tree
	 */
	public static String getMerkleString(
			final MerkleNode root,
			final int maxDepth,
			final boolean printNodeDescription) {
		if (root == null) {
			return "-(root) null";
		}
		final StringBuilder sb = new StringBuilder();
		buildMerkleString(sb, root, 1, 0, 0, "", maxDepth, printNodeDescription);
		return sb.toString();
	}

	/**
	 * Return a string containing debug information about a node.
	 */
	public static String merkleDebugString(final MerkleNode node) {
		final StringBuilder sb = new StringBuilder();

		sb.append("(");

		if (node == null) {
			sb.append("null");
		} else {
			sb.append(node.getClass().getName());
			sb.append(": route = ").append(node.getRoute());
			sb.append(", released = ").append(node.isReleased());
			sb.append(", ref count = ").append(node.getReferenceCount());
			sb.append(", immutable = ").append(node.isImmutable());
			if (!node.isLeaf()) {
				sb.append(", child count = ").append(node.asInternal().getNumberOfChildren());
			}
		}

		sb.append(")");

		return sb.toString();
	}

	/**
	 * Find the index of a given child within its parent. O(n) time in the number of children of the parent.
	 * Sufficiently fast for tiny nodes (i.e. binary nodes), too slow for large nary nodes.
	 *
	 * @param parent
	 * 		the parent in question
	 * @param child
	 * 		the child in question
	 * @return the index of the child within the parent
	 */
	public static int findChildPositionInParent(final MerkleInternal parent, final MerkleNode child) {
		for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
			if (child == parent.getChild(childIndex)) {
				return childIndex;
			}
		}
		throw new IllegalStateException("node is not a child of the given parent");
	}

}

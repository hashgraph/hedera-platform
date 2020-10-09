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

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class MerkleUtils {

	/**
	 * Invalidate the hashes of an entire tree (or subtree) rooted at the given node.
	 *
	 * @param root
	 * 		the root of the tree (or subtree)
	 */
	public static void invalidateTree(MerkleNode root) {
		if (root != null) {
			root.forEachNode(MerkleNode::invalidateHash);
		}
	}

	/**
	 * Rehash the entire tree, discarding any hashes that are already computed.
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

	private static void buildMerkleString(StringBuilder sb, MerkleNode node, int depth, int indexInParent,
			int parentNumberOfChildren, String indentation, int maxDepth, final boolean printNodeDescription) {

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
			sb.append(classElements[classElements.length - 1]).append(": " + (printNodeDescription ? node.toString() : ""));

			if (node.isLeaf()) {
				sb.append(node.toString()).append("\n");
			} else {
				MerkleInternal internal = (MerkleInternal) node;
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
	 */
	public static String getMerkleString(MerkleNode root) {
		return getMerkleString(root, false);
	}

	public static String getMerkleString(MerkleNode root, final boolean printNodeDescription) {
		return getMerkleString(root, -1, printNodeDescription);
	}

	/**
	 * Get a string representing a merkle tree. Do not include nodes below a given depth.
	 */
	public static String getMerkleString(MerkleNode root, int maxDepth, final boolean printNodeDescription) {
		if (root == null) {
			return "-(root) null";
		}
		StringBuilder sb = new StringBuilder();
		buildMerkleString(sb, root, 1, 0, 0, "", maxDepth, printNodeDescription);
		return sb.toString();
	}

}

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

package com.swirlds.common.merkle.synchronization;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.iterators.MerkleBreadthFirstIterator;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import static com.swirlds.common.merkle.synchronization.TreeComparer.MerkleNodeDiffReason.*;

public abstract class TreeComparer {

	public static MerkleNodeDiff findNextDiff(Iterator<MerkleNode> it1, Iterator<MerkleNode> it2) {
		while (it1.hasNext()) {

			if (!it2.hasNext()) {
				return new MerkleNodeDiff(it1.next(), null, ITERATOR_MISMATCH);
			}

			MerkleNode node1 = it1.next();
			MerkleNode node2 = it2.next();

			if (node1 == null && node2 == null) {
				continue;
			}

			if (node1 == null || node2 == null) {
				return new MerkleNodeDiff(node1, node2, NODE_NULL);
			}

			if (node1.getClass() != node2.getClass()) {
				return new MerkleNodeDiff(node1, node2, CLASS_DIFFERENT);
			}

			if (node1.isLeaf() != node2.isLeaf()) {
				return new MerkleNodeDiff(node1, node2, DIFF_TYPE);
			}

			if (node1.isLeaf()) {
				if (!Objects.equals(node1.getHash(), node2.getHash())) {
					return new MerkleNodeDiff(node1, node2, HASH_DIFF);
				}

//				if (!Objects.equals(node1, node2)) {
//					return new MerkleNodeDiff(node1, node2, NOT_EQUALS);
//				}
			} else {
				MerkleInternal int1 = (MerkleInternal) node1;
				MerkleInternal int2 = (MerkleInternal) node2;

				if (int1.getNumberOfChildren() != int2.getNumberOfChildren()) {
					return new MerkleNodeDiff(node1, node2, CHILD_COUNT);
				}
			}
		}
		if (it2.hasNext()) {
			return new MerkleNodeDiff(null, it2.next(), ITERATOR_MISMATCH);
		}

		return null;
	}

	public static MerkleNodeDiff findFirstDiff(MerkleNode root1, MerkleNode root2) {
		Iterator<MerkleNode> it1 = new MerkleBreadthFirstIterator<>(root1);
		Iterator<MerkleNode> it2 = new MerkleBreadthFirstIterator<>(root2);

		return findNextDiff(it1, it2);
	}

	private static class DiffIterator implements Iterator<MerkleNodeDiff> {

		private final Iterator<MerkleNode> it1;
		private final Iterator<MerkleNode> it2;

		private MerkleNodeDiff next;

		private boolean iteratorsOutOfSync;

		public DiffIterator(MerkleNode root1, MerkleNode root2) {
			it1 = new MerkleBreadthFirstIterator<>(root1);
			it2 = new MerkleBreadthFirstIterator<>(root2);
			iteratorsOutOfSync = false;
		}

		private void findNext() {
			if (next != null || iteratorsOutOfSync) {
				return;
			}
			next = TreeComparer.findNextDiff(it1, it2);

			if (next != null && next.getReason() != HASH_DIFF) {
				// Once we get the first non-hash diff the iterators will be out of sync
				iteratorsOutOfSync = true;
			}
		}

		@Override
		public boolean hasNext() {
			findNext();
			return next != null;
		}

		@Override
		public MerkleNodeDiff next() {
			findNext();
			if (next == null) {
				throw new NoSuchElementException();
			}
			MerkleNodeDiff ret = next;
			next = null;
			return ret;
		}
	}

	/**
	 * Returns an iterator that walks over the differences between the trees. May not walk over all differences --
	 * this is a limitation of the merkle interface until routes are implemented. Stops when a difference is found
	 * that is not a simple HASH_DIFF.
	 */
	public static Iterator<MerkleNodeDiff> getDiffIterator(MerkleNode root1, MerkleNode root2) {
		return new DiffIterator(root1, root2);
	}

	public enum MerkleNodeDiffReason {
		NODE_NULL,
		CLASS_DIFFERENT,
		DIFF_TYPE,
		HASH_DIFF,
		NOT_EQUALS,
		CHILD_COUNT,
		ITERATOR_MISMATCH
	}

	public static class MerkleNodeDiff {
		private final MerkleNode node1;
		private final MerkleNode node2;
		private final MerkleNodeDiffReason reason;

		public MerkleNodeDiff(MerkleNode node1, MerkleNode node2,
				MerkleNodeDiffReason reason) {
			this.node1 = node1;
			this.node2 = node2;
			this.reason = reason;
		}

		public MerkleNode getNode1() {
			return node1;
		}

		public MerkleNode getNode2() {
			return node2;
		}

		public MerkleNodeDiffReason getReason() {
			return reason;
		}

		@Override
		public String toString() {
			return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
					.append("node1Class", node1 == null ? null : node1.getClass())
					.append("node2Class", node1 == null ? null : node2.getClass())
					.append("node1", node1)
					.append("node2", node2)
					.append("reason", reason)
					.toString();
		}
	}
}

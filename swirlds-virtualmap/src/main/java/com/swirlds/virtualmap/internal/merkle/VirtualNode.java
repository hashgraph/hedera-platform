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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;

import java.util.Objects;

/**
 * A base class for both {@link VirtualInternalNode} and {@link VirtualLeafNode}. Contains the base infrastructure
 * needed for a node, including handling of MerkleRoutes and other things. Handles lazy loading of the
 * hash and parent. Paths are never changed on an internal node (the node itself is either added or removed,
 * but keeps the same path always), whereas leaf nodes may have their path change when other leaves are
 * added or removed (leaves are moved around the tree).
 */
public abstract class VirtualNode<K extends VirtualKey<? super K>, V extends VirtualValue, R extends VirtualRecord>
		extends AbstractMerkleNode {

	/**
	 * The {@link VirtualMap} associated with this node. Nodes cannot be moved from one map
	 * to another.
	 */
	protected VirtualRootNode<K, V> root;

	/**
	 * The {@link VirtualRecord} is the backing data for this node. There are different types
	 * of records, {@link VirtualInternalRecord} for internal nodes and
	 * {@link com.swirlds.virtualmap.datasource.VirtualLeafRecord} for leaf nodes.
	 */
	protected R virtualRecord;

	protected VirtualNode() {
	}

	/**
	 * This constructor is called only by the subclasses.
	 *
	 * @param root
	 * 		The root associated with this node. Cannot be null.
	 * @param virtualRecord
	 * 		The record associated with this node. Cannot be null.
	 */
	protected VirtualNode(final VirtualRootNode<K, V> root, final R virtualRecord) {
		this.root = Objects.requireNonNull(root);
		this.virtualRecord = Objects.requireNonNull(virtualRecord);
		setHash(virtualRecord.getHash());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof VirtualNode)) {
			return false;
		}

		final VirtualNode<?, ?, ?> that = (VirtualNode<?, ?, ?>) o;
		return virtualRecord.equals(that.virtualRecord);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(virtualRecord);
	}
}

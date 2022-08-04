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

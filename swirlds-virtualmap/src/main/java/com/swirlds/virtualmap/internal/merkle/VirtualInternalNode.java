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

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.MerkleSerializationStrategy;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.Path;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getLeftChildPath;
import static com.swirlds.virtualmap.internal.Path.getRightChildPath;

/**
 * Represents a virtual internal merkle node.
 */
public final class VirtualInternalNode<K extends VirtualKey<? super K>, V extends VirtualValue>
		extends VirtualNode<K, V, VirtualInternalRecord>
		implements MerkleInternal {

	private static final int NUMBER_OF_CHILDREN = 2;

	public static final long CLASS_ID = 0xaf2482557cfdb6bfL;
	public static final int SERIALIZATION_VERSION = 1;

	private static final Set<MerkleSerializationStrategy> STRATEGIES = Set.of();

	public VirtualInternalNode() {

	}

	public VirtualInternalNode(final VirtualRootNode<K, V> root, final VirtualInternalRecord virtualRecord) {
		super(root, virtualRecord);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return NUMBER_OF_CHILDREN;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends MerkleNode> T getChild(final int i) {
		final VirtualNode<K, V, ? extends VirtualRecord> node;
		if (i == 0) {
			node = getLeft();
		} else if (i == 1) {
			node = getRight();
		} else {
			return null;
		}

		if (node == null) {
			return null;
		}

		final long targetPath = node.virtualRecord.getPath();
		// 0 is VirtualMapState and 1 is the root of the VirtualTree
		final List<Integer> routePath = Path.getRouteStepsFromRoot(targetPath);
		final MerkleRoute nodeRoute = this.root.getRoute().extendRoute(routePath);
		node.setRoute(nodeRoute);
 		return (T) node;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setChild(final int index, final MerkleNode merkleNode) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setChild(
			final int index,
			final MerkleNode merkleNode,
			final MerkleRoute merkleRoute,
			final boolean mayBeImmutable) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Always returns an ephemeral node, or one we already know about.
 	 */
	private VirtualNode<K, V, ? extends VirtualRecord> getLeft() {
		return getChild(getLeftChildPath(virtualRecord.getPath()));
	}

	/**
	 * Always returns an ephemeral node, or one we already know about.
	 */
	private VirtualNode<K, V, ? extends VirtualRecord> getRight() {
		return getChild(getRightChildPath(virtualRecord.getPath()));
	}

	private VirtualNode<K, V, ? extends VirtualRecord> getChild(final long childPath) {
		if (childPath < root.getState().getFirstLeafPath()) {
			return getInternalNode(childPath);
		} else {
			return getLeafNode(childPath);
		}
	}

	/**
	 * Locates and returns an internal node based on the given path. A new instance
	 * is returned each time.
	 *
	 * @param path
	 * 		The path of the node to find. If INVALID_PATH, null is returned.
	 * @return The node. Only returns null if INVALID_PATH was the path.
	 */
	private VirtualInternalNode<K, V> getInternalNode(final long path) {
		assert path != INVALID_PATH :
				"Cannot happen. Path will be a child of virtual record path every time.";

		assert path < root.getState().getFirstLeafPath();
		VirtualInternalRecord rec = root.getCache().lookupInternalByPath(path, false);
		if (rec == null) {
			try {
				rec = root.getDataSource().loadInternalRecord(path);
				if (rec == null) {
					return new VirtualInternalNode<>(root, new VirtualInternalRecord(path));
				}
			} catch (final IOException ex) {
				throw new RuntimeException("Failed to read a internal record from the data source", ex);
			}
		}

		return new VirtualInternalNode<>(root, rec);
	}

	/**
	 * @param path
	 * 		The path. Must not be null and must be a valid path
	 * @return The leaf, or null if there is not one.
	 * @throws RuntimeException
	 * 		If we fail to access the data store, then a catastrophic error occurred and
	 * 		a RuntimeException is thrown.
	 */
	private VirtualLeafNode<K, V> getLeafNode(final long path) {
		// If the code was properly written, this will always hold true.
		assert path != INVALID_PATH;
		assert path != ROOT_PATH;

		// If the path is not a valid leaf path then return null
		if (path < root.getState().getFirstLeafPath() || path > root.getState().getLastLeafPath()) {
			return null;
		}

		// Check the cache first
		VirtualLeafRecord<K, V> rec = root.getCache().lookupLeafByPath(path, false);

		// On cache miss, check the data source. It *has* to be there.
		if (rec == null) {
			try {
				rec = root.getDataSource().loadLeafRecord(path);
				// This should absolutely be impossible. We already checked to make sure the path falls
				// within the firstLeafPath and lastLeafPath, and we already failed to find the leaf
				// in the cache. It **MUST** be on disk, or we have a broken system.
				if (rec == null) {
					throw new IllegalStateException("Attempted to read from disk but couldn't find the leaf.");
				}
			} catch (final IOException ex) {
				throw new RuntimeException("Failed to read a leaf record from the data source", ex);
			}
		}

		return new VirtualLeafNode<>(root, rec);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<MerkleSerializationStrategy> supportedSerialization(final int version) {
		return STRATEGIES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualInternalNode<K, V> copy() {
		throw new UnsupportedOperationException("Don't use this. Need a map pointer.");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return SERIALIZATION_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "VirtualInternalNode{" + virtualRecord + "}";
	}
}

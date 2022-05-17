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

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.StateAccessor;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

/**
 * Implementation of {@link RecordAccessor} which, given a state, cache, and data source, provides access
 * to all records.
 *
 * @param <K>
 *     The key
 * @param <V>
 *     The value
 */
public class RecordAccessorImpl<K extends VirtualKey<? super K>, V extends VirtualValue>
		implements RecordAccessor<K, V> {
	private final StateAccessor state;
	private final VirtualNodeCache<K, V> cache;
	private final VirtualDataSource<K, V> dataSource;

	/**
	 * Create a new {@link RecordAccessorImpl}.
	 *
	 * @param state
	 * 		The state. Cannot be null.
	 * @param cache
	 * 		The cache. Cannot be null.
	 * @param dataSource
	 * 		The data source. Can be null.
	 */
	public RecordAccessorImpl(StateAccessor state, VirtualNodeCache<K, V> cache, VirtualDataSource<K, V> dataSource) {
		this.state = Objects.requireNonNull(state);
		this.cache = Objects.requireNonNull(cache);
		this.dataSource = dataSource;
	}

	/**
	 * Gets the state.
	 *
	 * @return
	 * 		The state. This will never be null.
	 */
	public StateAccessor getState() {
		return state;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualNodeCache<K, V> getCache() {
		return cache;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualDataSource<K, V> getDataSource() {
		return dataSource;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualRecord findRecord(final long path) {
		assert path != INVALID_PATH;
		if (path >= ROOT_PATH && path < state.getFirstLeafPath()) {
			return findInternalRecord(path);
		} else {
			assert path >= state.getFirstLeafPath();
			return findLeafRecord(path, false);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualInternalRecord findInternalRecord(final long path) {
		assert path >= 0;

		VirtualInternalRecord node = cache.lookupInternalByPath(path, false);
		if (node == null) {
			try {
				node = dataSource.loadInternalRecord(path);
			} catch (final IOException e) {
				throw new UncheckedIOException(
						"Failed to read an internal node record from the data source", e);
			}
		}

		return node == VirtualNodeCache.DELETED_INTERNAL_RECORD ? null : node;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualLeafRecord<K, V> findLeafRecord(final K key, final boolean copy) {
		VirtualLeafRecord<K, V> rec = cache.lookupLeafByKey(key, copy);
		if (rec == null) {
			try {
				rec = dataSource.loadLeafRecord(key);
				if (rec != null && copy) {
					assert rec.getKey().equals(key)
							: "The key we found from the DB does not match the one we were looking for! key=" + key;
					cache.putLeaf(rec);
				}
			} catch (final IOException ex) {
				throw new UncheckedIOException("Failed to read a leaf record from the data source by key", ex);
			}
		}

		return rec == VirtualNodeCache.DELETED_LEAF_RECORD ? null : rec;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualLeafRecord<K, V> findLeafRecord(final long path, final boolean copy) {
		assert path != INVALID_PATH;
		assert path != ROOT_PATH;

		if (path < state.getFirstLeafPath() || path > state.getLastLeafPath()) {
			return null;
		}

		VirtualLeafRecord<K, V> rec = cache.lookupLeafByPath(path, copy);
		if (rec == null) {
			try {
				rec = dataSource.loadLeafRecord(path);
				if (rec != null && copy) {
					cache.putLeaf(rec);
				}
			} catch (final IOException ex) {
				throw new UncheckedIOException("Failed to read a leaf record from the data source by path", ex);
			}
		}

		return rec == VirtualNodeCache.DELETED_LEAF_RECORD ? null : rec;
	}
}

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

package com.swirlds.virtualmap.internal;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;

import java.io.UncheckedIOException;

/**
 * Provides access to all records.
 * @param <K>
 *     The key
 * @param <V>
 *     The value
 */
public interface RecordAccessor<K extends VirtualKey<? super K>, V extends VirtualValue> {

	/**
	 * Gets the {@link VirtualRecord} at a given path. If there is no record at tht path, null is returned.
	 *
	 * @param path
	 * 		The path of the node.
	 * @return
	 * 		Null if the record doesn't exist. Either the path is bad, or the record has been deleted,
	 * 		or the record has never been created.
	 * @throws UncheckedIOException
	 * 		If we fail to access the data store, then a catastrophic error occurred and
	 * 		an UncheckedIOException is thrown.
	 */
	VirtualRecord findRecord(final long path);

	/**
	 * Gets the {@link VirtualInternalRecord} at a given path. If there is no
	 * record at tht path, null is returned.
	 *
	 * @param path
	 * 		The path of the internal node.
	 * @return
	 * 		Null if the internal record doesn't exist. Either the path is bad, or the record has been deleted,
	 * 		or the record has never been created.
	 * @throws UncheckedIOException
	 * 		If we fail to access the data store, then a catastrophic error occurred and
	 * 		an UncheckedIOException is thrown.
	 */
	VirtualInternalRecord findInternalRecord(long path);

	/**
	 * Locates and returns a leaf node based on the given key. If the leaf
	 * node already exists in memory, then the same instance is returned each time.
	 * If the node is not in memory, then a new instance is returned. To save
	 * it in memory, set <code>cache</code> to true. If the key cannot be found in
	 * the data source, then null is returned.
	 *
	 * @param key
	 * 		The key. Must not be null.
	 * @param copy
	 * 		Whether to make a fast copy if needed.
	 * @return The leaf, or null if there is not one.
	 * @throws UncheckedIOException
	 * 		If we fail to access the data store, then a catastrophic error occurred and
	 * 		an UncheckedIOException is thrown.
	 */
	VirtualLeafRecord<K, V> findLeafRecord(final K key, final boolean copy);

	/**
	 * Locates and returns a leaf node based on the path. If the leaf
	 * node already exists in memory, then the same instance is returned each time.
	 * If the node is not in memory, then a new instance is returned. To save
	 * it in memory, set <code>cache</code> to true. If the leaf cannot be found in
	 * the data source, then null is returned.
	 *
	 * @param path
	 * 		The path
	 * @param copy
	 * 		Whether to make a fast copy if needed.
	 * @return The leaf, or null if there is not one.
	 * @throws UncheckedIOException
	 * 		If we fail to access the data store, then a catastrophic error occurred and
	 * 		an UncheckedIOException is thrown.
	 */
	VirtualLeafRecord<K, V> findLeafRecord(final long path, final boolean copy);

	/**
	 * Gets the data source backed by this {@link RecordAccessor}
	 *
	 * @return
	 * 		The data source. Will not be null.
	 */
	VirtualDataSource<K,V> getDataSource(); // I'd actually like to remove this some day...

	/**
	 * Gets the cache.
	 *
	 * @return
	 * 		The cache. This will never be null.
	 */
	VirtualNodeCache<K, V> getCache();
}

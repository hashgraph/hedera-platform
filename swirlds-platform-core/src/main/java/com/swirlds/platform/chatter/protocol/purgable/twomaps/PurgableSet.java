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

package com.swirlds.platform.chatter.protocol.purgable.twomaps;


import com.swirlds.common.threading.locks.Locked;
import com.swirlds.common.threading.locks.IndexLock;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A thread safe set-like object that supports purging by generation.
 *
 * @param <T>
 * 		the type of the object in the set
 */
public class PurgableSet<T> {

	public static final int DEFAULT_PARALLELISM = 1024;
	/**
	 * The data in the set.
	 */
	private final Set<T> data = ConcurrentHashMap.newKeySet();

	/**
	 * Organize keys by generation. Used for purging.
	 */
	private final Map<Long, Set<T>> generationMap = new ConcurrentHashMap<>();

	private final Function<T, Long> getGenerationFromItem;

	private final AtomicLong smallestAllowedGeneration = new AtomicLong(0);

	/**
	 * When inserting data into this data structure, it is critical that data is not inserted after the
	 * data's generation has been purged (as this would lead to a memory leak). Whenever new data is inserted,
	 * acquire a lock that prevents concurrent purging of that generation.
	 */
	private final IndexLock lock = new IndexLock(DEFAULT_PARALLELISM);

	/**
	 * Create a new purgable set.
	 *
	 * @param getGenerationFromItem
	 * 		a lambda that returns the generation associated with each item
	 */
	public PurgableSet(final Function<T, Long> getGenerationFromItem) {
		this.getGenerationFromItem = Objects.requireNonNull(getGenerationFromItem);
	}

	/**
	 * Get the generation of an item.
	 *
	 * @param item
	 * 		the item
	 * @return the item's generation
	 */
	private long getGeneration(final T item) {
		return getGenerationFromItem.apply(item);
	}

	/**
	 * Check if an item belongs to a purged generation.
	 *
	 * @param item
	 * 		the item
	 * @return true if the item belongs to a purged generation
	 */
	private boolean isPurged(final T item) {
		return getGenerationFromItem.apply(item) < smallestAllowedGeneration.get();
	}

	/**
	 * Add an item to the map that tracks items by generation. Should only be called in locked blocks.
	 *
	 * @param item
	 * 		the key to add
	 */
	private void addToGenerationMap(final T item) {
		final Set<T> itemsInGeneration = generationMap.computeIfAbsent(getGeneration(item), k -> new HashSet<>());
		itemsInGeneration.add(item);
	}

	/**
	 * Add an item to the set
	 *
	 * @param item
	 * 		the item to add
	 * @return true if the item was added, false if the set already contained the item or if the item belongs
	 * 		to a purged generation
	 */
	public boolean add(final T item) {
		try (final Locked ignored = lock.autoLock(getGeneration(item))) {
			if (isPurged(item)) {
				return false;
			}

			final boolean ret = data.add(item);
			if (ret) {
				addToGenerationMap(item);
			}
			return ret;
		}
	}

	/**
	 * Remove an item from the set.
	 *
	 * @param item
	 * 		the item to remove
	 * @return if the item was present and is now removed, false if the item was not present
	 */
	public boolean remove(final T item) {

		// Intentionally do not clean up the generationMap. This data structure will self-clean
		// when the appropriate generation is purged.

		return data.remove(item);
	}

	/**
	 * Check if the set contains the item.
	 *
	 * @param item
	 * 		the item in question
	 * @return true if the set contains the item
	 */
	public boolean contains(final T item) {
		return data.contains(item);
	}

	/**
	 * Purge a single generation.
	 *
	 * @param generation
	 * 		the generation to be purged
	 */
	private void purgeGeneration(final long generation) {
		try (final Locked ignored = lock.autoLock(generation)) {
			final Set<T> items = generationMap.remove(generation);
			if (items == null) {
				// No data from this generation is present
				return;
			}
			for (final T item : items) {
				data.remove(item);
			}
			smallestAllowedGeneration.set(generation + 1);
		}
	}

	/**
	 * Purge all data associated with generations smaller than a specified generation.
	 *
	 * @param smallerThanGeneration
	 * 		all data associated with smaller generations will be erased
	 */
	public void purge(final long smallerThanGeneration) {
		final long smallestGeneration = smallestAllowedGeneration.get();
		if (smallerThanGeneration < smallestGeneration) {
			// generation has already been purged
			return;
		}

		for (long generation = smallestGeneration; generation < smallerThanGeneration; generation++) {
			purgeGeneration(generation);
		}
	}
}

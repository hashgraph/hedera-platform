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

import com.swirlds.platform.chatter.protocol.purgable.PurgableMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A map-like object that supports efficient purging.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public abstract class AbstractPurgableDoubleMap<K, V> implements PurgableMap<K, V> {

	/**
	 * The data in the map.
	 */
	private final Map<K, V> data = buildDataMap();

	/**
	 * Organize keys by generation. Used for purging.
	 */
	private final Map<Long, Set<K>> generationMap = buildGenerationMap();

	/**
	 * A method that gets the generation associated with a given key.
	 */
	private final Function<K, Long> getGenerationFromKey;

	/**
	 * Construct an abstract purgable map.
	 *
	 * @param getGenerationFromKey
	 * 		a method that extracts the generation from a key
	 */
	protected AbstractPurgableDoubleMap(final Function<K, Long> getGenerationFromKey) {
		this.getGenerationFromKey = getGenerationFromKey;
	}

	/**
	 * Set the smallest allowed generation. All smaller generations are considered to be purged.
	 *
	 * @param generation
	 * 		the smallest allowed generation
	 */
	protected abstract void setSmallestAllowedGeneration(final long generation);

	/**
	 * Build the map to hold data.
	 *
	 * @return a map
	 */
	protected abstract Map<K, V> buildDataMap();

	/**
	 * Build the map to hold generation data.
	 *
	 * @return a map
	 */
	protected abstract Map<Long, Set<K>> buildGenerationMap();

	/**
	 * Acquire an exclusive lock on a generation.
	 *
	 * @param generation
	 * 		the generation number
	 */
	protected void lockGeneration(final long generation) {
		// Override this if generations require locking
	}

	/**
	 * Release an exclusive lock on a generation.
	 *
	 * @param generation
	 * 		the generation number
	 */
	protected void unlockGeneration(final long generation) {
		// Override this if generations require locking
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V get(final K key) {
		return data.get(key);
	}

	/**
	 * Add a key to the map that tracks keys by generation. Should only be called in locked blocks.
	 *
	 * @param key
	 * 		the key to add
	 */
	private void addToGenerationMap(final K key) {
		final Set<K> keysInGeneration = generationMap.computeIfAbsent(getGeneration(key), k -> new HashSet<>());
		keysInGeneration.add(key);
	}

	/**
	 * Get the generation from a key.
	 *
	 * @param key
	 * 		the key
	 * @return the associated generation
	 */
	private long getGeneration(final K key) {
		return getGenerationFromKey.apply(key);
	}

	/**
	 * Check if a key belongs to a purged generation.
	 *
	 * @param key
	 * 		the key
	 * @return true if the key belongs to a purged generation
	 */
	private boolean isPurged(final K key) {
		return getGeneration(key) < getPurgedGeneration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
		V value = data.get(key);

		if (value == null) {
			final long generation = getGeneration(key);
			lockGeneration(generation);

			if (isPurged(key)) {
				// this generation has been purged
				unlockGeneration(generation);
				return null;
			}

			value = data.computeIfAbsent(key, mappingFunction);

			// This may add a key to a generation map more than once, but the overhead of doing so is minimal.
			addToGenerationMap(key);
			unlockGeneration(generation);
		}
		return value;
	}

	/**
	 * Insert a value if there is currently no entry for the value. More efficient than {@link #put(Object, Object)}
	 * if there are many duplicate keys being inserted into the map. Less efficient than {@link #put(Object, Object)}
	 * if duplicates are sufficiently rare.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 * @return the inserted value, or the previous value if the key was already in the map. Null if the generation
	 * 		has been purged.
	 */
	@Override
	public V putIfAbsent(final K key, final V value) {
		V ret = data.get(key);

		if (ret == null) {
			final long generation = getGeneration(key);
			lockGeneration(generation);

			if (isPurged(key)) {
				unlockGeneration(generation);
				return null;
			}

			ret = data.putIfAbsent(key, value);
			if (ret == null) {
				addToGenerationMap(key);
			}

			unlockGeneration(generation);
		}

		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V put(final K key, final V value) {
		final long generation = getGeneration(key);
		lockGeneration(generation);

		if (isPurged(key)) {
			unlockGeneration(generation);
			return null;
		}

		final V ret = data.put(key, value);

		if (ret == null) {
			addToGenerationMap(key);
		}

		unlockGeneration(generation);
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V remove(final K key) {
		// Intentionally do not clean up the generationMap. This data structure will self-clean
		// when the appropriate generation is purged.

		return data.remove(key);
	}

	/**
	 * Purge a single generation.
	 *
	 * @param generation
	 * 		the generation to be purged
	 * @param purgedValueHandler
	 * 		handles purged values
	 */
	private void purgeGeneration(final long generation, final BiConsumer<K, V> purgedValueHandler) {
		lockGeneration(generation);

		final Set<K> keys = generationMap.remove(generation);
		if (keys == null) {
			// no data for generation
			unlockGeneration(generation);
			return;
		}

		for (final K key : keys) {
			final V value = data.remove(key);
			// if an entry has been removed, it is only removed from the data map, not the generation map
			// in this case, the value might be null, so we don't provide it to the value handler
			if (value != null && purgedValueHandler != null) {
				purgedValueHandler.accept(key, value);
			}
		}
		setSmallestAllowedGeneration(generation + 1);

		unlockGeneration(generation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void purge(final long smallerThanGeneration) {
		purge(smallerThanGeneration, null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void purge(final long smallerThanGeneration, final BiConsumer<K, V> purgedValueHandler) {
		final long smallestAllowedGeneration = getPurgedGeneration();
		if (smallerThanGeneration < smallestAllowedGeneration) {
			// generation has already been purged
			return;
		}

		for (long generation = smallestAllowedGeneration; generation < smallerThanGeneration; generation++) {
			purgeGeneration(generation, purgedValueHandler);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSize() {
		return data.size();
	}

	@Override
	public void clear() {
		data.clear();
		generationMap.clear();
	}
}

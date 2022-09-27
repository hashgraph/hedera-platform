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

package com.swirlds.common.sequence.map.internal;

import com.swirlds.common.sequence.map.SequenceMap;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Boilerplate implementation for {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public abstract class AbstractSequenceMap<K, V> implements SequenceMap<K, V> {

	/**
	 * The data in the map.
	 */
	private final Map<K, V> data = buildDataMap();

	/**
	 * Organize keys by sequence number.
	 */
	private final Map<Long, Set<K>> sequenceMap = buildSequenceMap();

	/**
	 * A method that gets the sequence number associated with a given key.
	 */
	private final ToLongFunction<K> getSequenceNumberFromKey;

	/**
	 * When this object is cleared, the lowest allowed sequence number is reset to this value.
	 */
	private final long initialLowestAllowedSequenceNumber;

	/**
	 * When this object is cleared, the highest allowed sequence number is reset to this value.
	 */
	private final long initialHighestAllowedSequenceNumber;

	/**
	 * Construct an abstract purgable map.
	 *
	 * @param initialLowestAllowedSequenceNumber
	 * 		the lowest allowed sequence number when this object is constructed,
	 * 		or after it is cleared
	 * @param initialHighestAllowedSequenceNumber
	 * 		the highest allowed sequence number when this object is constructed,
	 * 		or after it is cleared
	 * @param getSequenceNumberFromKey
	 * 		a method that extracts the sequence number from a 1key
	 */
	protected AbstractSequenceMap(
			final long initialLowestAllowedSequenceNumber,
			final long initialHighestAllowedSequenceNumber,
			final ToLongFunction<K> getSequenceNumberFromKey) {

		this.initialLowestAllowedSequenceNumber = initialLowestAllowedSequenceNumber;
		this.initialHighestAllowedSequenceNumber = initialHighestAllowedSequenceNumber;
		this.getSequenceNumberFromKey = getSequenceNumberFromKey;
	}

	/**
	 * Set the smallest allowed sequence number.
	 *
	 * @param sequenceNumber
	 * 		the smallest allowed sequence number
	 */
	protected abstract void setLowestAllowedSequenceNumber(final long sequenceNumber);

	/**
	 * Set the largest allowed sequence number.
	 *
	 * @param sequenceNumber
	 * 		the largest allowed sequence number
	 */
	protected abstract void setHighestAllowedSequenceNumber(final long sequenceNumber);

	/**
	 * Build the map to hold data.
	 *
	 * @return a map
	 */
	protected abstract Map<K, V> buildDataMap();

	/**
	 * Build the map that organizes the data by sequence number.
	 *
	 * @return a map
	 */
	protected abstract Map<Long, Set<K>> buildSequenceMap();

	/**
	 * Acquire an exclusive lock on a sequence number. No-op for implementations that do not require thread safety.
	 *
	 * @param sequenceNumber
	 * 		the sequence number to lock
	 */
	protected void lockSequenceNumber(final long sequenceNumber) {
		// Override if thread safety is required
	}

	/**
	 * Release an exclusive lock on a sequence number. No-op for implementations that do not require thread safety.
	 *
	 * @param sequenceNumber
	 * 		the sequence number to unlock
	 */
	protected void unlockSequenceNumber(final long sequenceNumber) {
		// Override if thread safety is required
	}

	/**
	 * Acquire an exclusive lock on all sequence numbers. No-op for implementations that do not require thread safety.
	 */
	protected void fullLock() {
		// Override if thread safety is required
	}

	/**
	 * Release an exclusive lock on all sequence numbers. No-op for implementations that do not require thread safety.
	 */
	protected void fullUnlock() {
		// Override if thread safety is required
	}

	/**
	 * Acquire a lock on window management. Held during purge/expand calls, and during clear.
	 * No-op for implementations that do not require thread safety.
	 */
	protected void windowLock() {
		// Override if thread safety is required
	}

	/**
	 * Release a lock on window management. Held during purge/expand calls, and during clear.
	 * No-op for implementations that do not require thread safety.
	 */
	protected void windowUnlock() {
		// Override if thread safety is required
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V get(final K key) {
		return data.get(key);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean containsKey(final K key) {
		return data.containsKey(key);
	}

	/**
	 * Add a key to the map that tracks keys by sequence numbers. Should only be called in locked blocks.
	 *
	 * @param key
	 * 		the key to add
	 */
	private void addToSequenceMap(final K key) {
		final Set<K> keysWithSequenceNumber = sequenceMap.computeIfAbsent(getSequenceNumber(key), k -> new HashSet<>());
		keysWithSequenceNumber.add(key);
	}

	/**
	 * Get the sequence number from a key.
	 *
	 * @param key
	 * 		the key
	 * @return the associated sequence number
	 */
	private long getSequenceNumber(final K key) {
		return getSequenceNumberFromKey.applyAsLong(key);
	}

	/**
	 * <p>
	 * Check if a key has a sequence number that is outside the allowed window.
	 * </p>
	 *
	 * <p>
	 * Thread safety: this window may be adjusting concurrently with an attempt to insert something into
	 * this map.
	 * </p>
	 *
	 * <p>
	 * If the lower bound is shifting (as a result of a purge), there is no thread safety problem
	 * since we lock each index as it is purged.
	 * </p>
	 *
	 * <p>
	 * There are several cases to consider if the upper bound is shifting.
	 * </p>
	 *
	 * <ul>
	 * <li>
	 * If sequence number of the key being inserted is outside the allowed window before and after the shift then
	 * no harm, as it will be rejected if either the new or old window is used.
	 * </li>
	 * <li>
	 * If sequence number of the key being inserted is inside the allowed window before and after the shift then
	 * no harm, as it will be accepted if either the new or old window is used.
	 * </li>
	 * <li>
	 * If the sequence number of the key being inserted is outside the allowed window before the shift and inside
	 * the window after the shift, then there is a race condition of sorts. But this is not problematic, as the
	 * end result is indistinguishable from a legal sequential ordering of the two operations:
	 * (window shift -&gt; insertion, resulting in key being inserted) or
	 * (insertion -&gt; window shift, resulting in key being rejected).
	 * </li>
	 * <li>
	 * It's impossible for the upper bound of the window to decrease over time
	 * </li>
	 * </ul>
	 *
	 * @param sequenceNumber the sequence number in question
	 * @return true if the key has a sequence number that is not currently permitted to be in the data structure
	 */
	private boolean isOutsideAllowedWindow(final long sequenceNumber) {
		return sequenceNumber < getLowestAllowedSequenceNumber() || sequenceNumber > getHighestAllowedSequenceNumber();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
		V value = data.get(key);

		if (value == null) {
			final long sequenceNumber = getSequenceNumber(key);
			lockSequenceNumber(sequenceNumber);

			try {
				if (isOutsideAllowedWindow(sequenceNumber)) {
					// this sequence number is not permitted to be in the map
					return null;
				}

				value = data.computeIfAbsent(key, mappingFunction);

				// The key may already be in the map, but the cost of re-attempting insertion is minimal
				addToSequenceMap(key);
			} finally {
				unlockSequenceNumber(sequenceNumber);
			}
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
	 * @return the previous value if the key is currently in the map, or null if it is not currently in the map
	 */
	@Override
	public V putIfAbsent(final K key, final V value) {
		V ret = data.get(key);

		if (ret == null) {
			final long sequenceNumber = getSequenceNumber(key);

			lockSequenceNumber(sequenceNumber);

			try {
				if (isOutsideAllowedWindow(sequenceNumber)) {
					return null;
				}

				ret = data.putIfAbsent(key, value);
				if (ret == null) {
					addToSequenceMap(key);
				}
			} finally {
				unlockSequenceNumber(sequenceNumber);
			}
		}

		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V put(final K key, final V value) {
		final long sequenceNumber = getSequenceNumber(key);
		lockSequenceNumber(sequenceNumber);

		try {
			if (isOutsideAllowedWindow(sequenceNumber)) {
				return null;
			}

			final V ret = data.put(key, value);

			if (ret == null) {
				addToSequenceMap(key);
			}

			return ret;
		} finally {
			unlockSequenceNumber(sequenceNumber);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public V remove(final K key) {
		final long sequenceNumber = getSequenceNumber(key);

		lockSequenceNumber(sequenceNumber);
		try {
			final Set<K> keysWithSequenceNumber = sequenceMap.get(sequenceNumber);
			if (keysWithSequenceNumber != null) {
				keysWithSequenceNumber.remove(key);
			}

			return data.remove(key);
		} finally {
			unlockSequenceNumber(sequenceNumber);
		}
	}

	/**
	 * Purge all values with a particular sequence number and increase the smallest allowed sequence number by 1.
	 *
	 * @param sequenceNumber
	 * 		the sequence number to be purged
	 * @param purgedValueHandler
	 * 		handles purged values
	 */
	private void purgeSequenceNumber(final long sequenceNumber, final BiConsumer<K, V> purgedValueHandler) {
		lockSequenceNumber(sequenceNumber);

		try {
			removeSequenceNumberUnlocked(sequenceNumber, purgedValueHandler);
			setLowestAllowedSequenceNumber(sequenceNumber + 1);
		} finally {
			unlockSequenceNumber(sequenceNumber);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void purge(final long smallestAllowedSequenceNumber, final BiConsumer<K, V> removedValueHandler) {
		windowLock();

		try {
			final long currentSmallestSequenceNumber = getLowestAllowedSequenceNumber();
			if (smallestAllowedSequenceNumber < currentSmallestSequenceNumber) {
				// sequence number has already been purged
				return;
			}

			for (long sequenceNumber = currentSmallestSequenceNumber;
				 sequenceNumber < smallestAllowedSequenceNumber;
				 sequenceNumber++) {

				purgeSequenceNumber(sequenceNumber, removedValueHandler);
			}
		} finally {
			windowUnlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void expand(final long largestAllowedSequenceNumber) {
		windowLock();

		try {
			if (largestAllowedSequenceNumber <= getHighestAllowedSequenceNumber()) {
				// Maximum window size is only permitted to increase
				return;
			}
			setHighestAllowedSequenceNumber(largestAllowedSequenceNumber);
		} finally {
			windowUnlock();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeSequenceNumber(final long sequenceNumber, final BiConsumer<K, V> removedValueHandler) {
		lockSequenceNumber(sequenceNumber);
		removeSequenceNumberUnlocked(sequenceNumber, removedValueHandler);
		unlockSequenceNumber(sequenceNumber);
	}

	/**
	 * Remove all keys with a given sequence number. This method is not locked, the caller is expected to hold
	 * a lock on the sequence number that is being removed
	 *
	 * @param sequenceNumber
	 * 		the sequence number to remove
	 * @param removedValueHandler
	 * 		a callback that is passed each key/value pair that is removed
	 */
	private void removeSequenceNumberUnlocked(final long sequenceNumber, final BiConsumer<K, V> removedValueHandler) {
		final Set<K> keys = sequenceMap.remove(sequenceNumber);
		if (keys == null) {
			// no values are present for this sequence number
			return;
		}

		for (final K key : keys) {
			final V value = data.remove(key);
			if (removedValueHandler != null) {
				removedValueHandler.accept(key, value);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<K> getKeysWithSequenceNumber(final long sequenceNumber) {
		final List<K> list = new LinkedList<>();
		lockSequenceNumber(sequenceNumber);

		try {
			final Set<K> keysWithSequenceNumber = sequenceMap.get(sequenceNumber);
			if (keysWithSequenceNumber != null) {
				list.addAll(keysWithSequenceNumber);
			}

			return list;
		} finally {
			unlockSequenceNumber(sequenceNumber);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Map.Entry<K, V>> getEntriesWithSequenceNumber(final long sequenceNumber) {
		final List<Map.Entry<K, V>> list = new LinkedList<>();
		lockSequenceNumber(sequenceNumber);

		try {
			final Set<K> keysWithSequenceNumber = sequenceMap.get(sequenceNumber);
			if (keysWithSequenceNumber != null) {
				for (final K key : keysWithSequenceNumber) {
					list.add(new AbstractMap.SimpleEntry<>(key, data.get(key)));
				}
			}

			return list;
		} finally {
			unlockSequenceNumber(sequenceNumber);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSize() {
		return data.size();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		fullLock();
		windowLock();

		try {
			data.clear();
			sequenceMap.clear();
			setLowestAllowedSequenceNumber(initialLowestAllowedSequenceNumber);
			setHighestAllowedSequenceNumber(initialHighestAllowedSequenceNumber);
		} finally {
			windowUnlock();
			fullUnlock();
		}
	}
}

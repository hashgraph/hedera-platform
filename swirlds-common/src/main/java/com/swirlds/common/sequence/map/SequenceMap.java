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

package com.swirlds.common.sequence.map;

import com.swirlds.common.sequence.Purgable;
import com.swirlds.common.utility.Clearable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * <p>
 * A map-like object whose keys have an associated sequence number. Multiple keys may have the same sequence number.
 * </p>
 *
 * <p>
 * The sequence number of any particular object in this data structure is not permitted to change.
 * </p>
 *
 * <p>
 * This data structure is designed around use cases where the sequence number of new objects trends upwards over time.
 * This data structure may be significantly less useful for use cases where the window of allowable
 * sequence numbers shifts backwards and forwards arbitrarily.
 * </p>
 *
 * <p>
 * This data structure manages the allowed window of sequence numbers. That is, it allows a minimum and maximum
 * sequence number to be specified, and ensures that entries that violate that window are removed and not allowed
 * to enter. Increasing the minimum value allowed in the window is also called "purging", as it may cause values in the
 * map to be removed if they fall outside the window. Increasing the maximum value allowed in the window is called
 * "expanding", as it allows the insertion of values that would have previously been rejected.
 * </p>
 *
 * <p>
 * This data structure also allows all values with a particular sequence number to be efficiently
 * retrieved and deleted.
 * </p>
 *
 * @param <K>
 * 		the type of key
 * @param <V>
 * 		the type of value
 */
public interface SequenceMap<K, V> extends Purgable, Clearable {

	/**
	 * When a new {@link SequenceMap} is instantiated, this is the default lowest sequence number that is permitted
	 * to be added to the map.
	 */
	long DEFAULT_LOWEST_ALLOWED_SEQUENCE_NUMBER = 0;

	/**
	 * When a new {@link SequenceMap} is instantiated, this is the default highest sequence number that is permitted
	 * to be added to the map.
	 */
	long DEFAULT_HIGHEST_ALLOWED_SEQUENCE_NUMBER = Long.MAX_VALUE;

	/**
	 * Get the value for a key. Returns null if the key is not in the map. Also returns null if the key was once in the
	 * map but has since been purged.
	 *
	 * @param key
	 * 		the key
	 * @return the value, or null if the key is not present or has been purged
	 */
	V get(K key);

	/**
	 * Check if the map contains a key. Returns false if the key is not in the map. Also returns false if the key was
	 * once in the map but has since been purged.
	 *
	 * @param key
	 * 		the key in question
	 * @return true if the map currently contains the key
	 */
	boolean containsKey(K key);

	/**
	 * Get the value for a key. If none exists and the key's sequence number is permitted then create one.
	 *
	 * @param key
	 * 		the key
	 * @return the value, or null if the key has been purged
	 */
	V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

	/**
	 * Insert a value if there is currently no entry for the value, and it is legal to insert the key's sequence
	 * number (if this key's sequence number has previously been purged then this insertion will
	 * never be considered legal). Does not override the existing value if there is already an entry for this key
	 * in the map.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 * @return the inserted value, or the previous value if the key was already in the map. Null if the sequence
	 * 		number is not permitted
	 */
	V putIfAbsent(K key, V value);

	/**
	 * Insert a value into the map. No-op if key has a purged sequence number.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 * @return the previous value, or null if there was no previous value (or if the previous value was purged)
	 */
	V put(K key, V value);

	/**
	 * Remove an entry from the map.
	 *
	 * @param key
	 * 		the entry to remove
	 * @return the removed value, or null if the key was not present
	 */
	V remove(K key);

	/**
	 * Remove all keys with a given sequence number. Does not adjust the window of allowable sequence numbers,
	 * and so keys with this sequence number will not be rejected in the future.
	 *
	 * @param sequenceNumber
	 * 		all keys with this sequence number will be removed
	 */
	default void removeSequenceNumber(final long sequenceNumber) {
		removeSequenceNumber(sequenceNumber, null);
	}

	/**
	 * Remove all keys with a given sequence number. Does not adjust the window of allowable sequence numbers,
	 * and so keys with this sequence number will not be rejected in the future.
	 *
	 * @param sequenceNumber
	 * 		all keys with this sequence number will be removed
	 * @param removedValueHandler
	 * 		a callback that is passed all key/value pairs that are removed. Ignored if null.
	 */
	void removeSequenceNumber(final long sequenceNumber, BiConsumer<K, V> removedValueHandler);

	/**
	 * Get a list of all keys with a given sequence number. Once the list is returned, it is safe to modify
	 * the list without effecting the map that returned it. However, modification of the objects within the list
	 * may modify objects in the map that returned it.
	 *
	 * @param sequenceNumber
	 * 		the sequence number to get
	 * @return a list of keys that have the given sequence number
	 */
	List<K> getKeysWithSequenceNumber(final long sequenceNumber);

	/**
	 * Get a list of all entries with a given sequence number. Once the list is returned, it is safe to modify
	 * the list without effecting the map that returned it. However, modification of the objects within the list
	 * may modify objects in the map that returned it.
	 *
	 * @param sequenceNumber
	 * 		the sequence number to get
	 * @return a list of entries that have the given sequence number
	 */
	List<Map.Entry<K, V>> getEntriesWithSequenceNumber(final long sequenceNumber);

	/**
	 * Purge all keys that have a sequence number smaller than a specified value. After this operation,
	 * all keys with a smaller sequence number will be rejected if insertion is attempted.
	 *
	 * <p>
	 * The smallest allowed sequence number must only increase over time. If N&lt;=M and M has already been purged,
	 * then purging N will have no effect.
	 * </p>
	 *
	 * @param smallestAllowedSequenceNumber
	 * 		all keys with a sequence number strictly smaller than this value will be removed
	 */
	default void purge(final long smallestAllowedSequenceNumber) {
		purge(smallestAllowedSequenceNumber, null);
	}

	/**
	 * <p>
	 * Purge all keys that have a sequence number smaller than a specified value. After this operation,
	 * all keys with a smaller sequence number will be rejected if insertion is attempted.
	 * </p>
	 *
	 * <p>
	 * The smallest allowed sequence number must only increase over time. If N&lt;=M and M has already been purged,
	 * then purging N will have no effect.
	 * </p>
	 *
	 * @param smallestAllowedSequenceNumber
	 * 		all keys with a sequence number strictly smaller than this value will be removed
	 * @param removedValueHandler
	 * 		this value is passed each key/value pair that is removed as a result of this operation, ignored if null
	 */
	void purge(long smallestAllowedSequenceNumber, BiConsumer<K, V> removedValueHandler);

	/**
	 * <p>
	 * Specify a newly permitted maximum sequence number. After calling this method, keys with sequence numbers
	 * strictly greater than this value will be rejected, and keys with sequence numbers less than or equal to this
	 * value may be accepted (if they don't violate the minimum sequence number).
	 * </p>
	 *
	 * <p>
	 * The largest allowed sequence number must only increase over time. If N&lt;=M and N has already been expanded to,
	 * then then expanding to M will have no effect.
	 * </p>
	 *
	 * <p>
	 * Unlike {@link #purge(long)}, this method allows a wider range of sequence numbers to be included in this
	 * data structure, and does not have the capability of removing elements.
	 * </p>
	 *
	 * @param largestAllowedSequenceNumber
	 * 		the largest sequence number that is permitted to be in this data structure
	 */
	void expand(long largestAllowedSequenceNumber);

	/**
	 * @return the number of entries in this map
	 */
	int getSize();

	/**
	 * Get the minimum sequence number that is permitted to be in this map. All keys with smaller
	 * sequence numbers have been removed, and any key added in the future will be rejected
	 * if it has a smaller sequence number.
	 *
	 * @return the smallest allowed sequence number
	 */
	long getLowestAllowedSequenceNumber();

	/**
	 * Get the maximum sequence number that is permitted to be in this map. All keys with larger
	 * sequence numbers have been rejected, and any key added in the future will be rejected
	 * if it has a larger sequence number than what is returned by this method at that time.
	 *
	 * @return the largest allowed sequence number
	 */
	long getHighestAllowedSequenceNumber();

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Warning: this operation is moderately expensive for the concurrent implementation of this interface.
	 * </p>
	 */
	@Override
	void clear();
}

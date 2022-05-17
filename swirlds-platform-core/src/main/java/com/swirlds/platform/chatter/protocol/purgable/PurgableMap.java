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

package com.swirlds.platform.chatter.protocol.purgable;

import com.swirlds.platform.chatter.protocol.Purgable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A map whose data has a generation associated with it. The data in this map can be efficiently removed based on its
 * generation.
 *
 * @param <K>
 * 		the type of key
 * @param <V>
 * 		the type of value
 */
public interface PurgableMap<K, V> extends Purgable {
	/**
	 * Get the purged generation. All smaller generations are have been removed.
	 *
	 * @return the smallest contained generation
	 */
	long getPurgedGeneration();

	/**
	 * Get the value for a key.
	 *
	 * @param key
	 * 		the key
	 * @return the value, or null if the key is not present or has been purged
	 */
	V get(K key);

	/**
	 * Get the value for a key. If none exists and the key has not been purged then create one. If called on multiple
	 * threads, at most one new object will be instantiated.
	 *
	 * @param key
	 * 		the key
	 * @return the value, or null if the key has been purged
	 */
	V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

	/**
	 * Insert a value if there is currently no entry for the value.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 * @return the inserted value, or the previous value if the key was already in the map. Null if the generation
	 * 		has been purged.
	 */
	V putIfAbsent(K key, V value);

	/**
	 * Insert a value into the map. No-op if key belongs to a purged generation.
	 *
	 * @param key
	 * 		the key
	 * @param value
	 * 		the value
	 * @return the previous value, or null if there was no previous value
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
	 * Purge all data associated with generations smaller than a specified generation.
	 *
	 * @param smallerThanGeneration
	 * 		all data associated with smaller generations will be erased
	 * @param purgedValueHandler
	 * 		this method is passed each value that is purged
	 */
	void purge(long smallerThanGeneration, BiConsumer<K, V> purgedValueHandler);
}

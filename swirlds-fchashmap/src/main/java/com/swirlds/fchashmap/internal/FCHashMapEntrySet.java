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

package com.swirlds.fchashmap.internal;

import com.swirlds.fchashmap.FCHashMap;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;

/**
 * An entry set view for {@link FCHashMap}.
 *
 * @param <K>
 * 		the type of the map's key
 * @param <V>
 * 		the type of the map's value
 */
public class FCHashMapEntrySet<K, V> extends AbstractSet<Map.Entry<K, V>> {

	private final FCHashMap<K, V> map;
	private final Map<K, Mutation<V>> data;

	/**
	 * Create a new entry set view for an {@link FCHashMap}.
	 *
	 * @param map
	 * 		the map that will be represented by this set
	 */
	public FCHashMapEntrySet(final FCHashMap<K, V> map, final Map<K, Mutation<V>> data) {
		this.map = map;
		this.data = data;
	}

	/**
	 * {@inheritDoc}
	 */
	public Iterator<Map.Entry<K, V>> iterator() {
		return new FCHashMapEntrySetIterator<>(map, data);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int size() {
		return map.size();
	}
}

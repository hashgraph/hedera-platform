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

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * This iterator walks over an {@link FCHashMapEntrySet}.
 *
 * @param <K>
 * 		the type of the map's key
 * @param <V>
 * 		the type of the map's value
 */
public class FCHashMapEntrySetIterator<K, V> implements Iterator<Map.Entry<K, V>> {

	private final FCHashMap<K, V> map;

	private final Iterator<K> keyIterator;
	private K nextValidKey;
	private K previousValidKey;

	public FCHashMapEntrySetIterator(final FCHashMap<K, V> map, final Map<K, Mutation<V>> data) {
		this.map = map;
		keyIterator = data.keySet().iterator();
	}

	/**
	 * Some elements in the data iterator will not
	 * be in the current copy. After calling this
	 * method, nextValidKey will point to the
	 * next key in the map that is in the current
	 * copy of the map (if one exists))
	 */
	private void advanceIterator() {
		if (nextValidKey != null) {
			return;
		}
		while (keyIterator.hasNext()) {
			K nextKey = keyIterator.next();
			if (map.containsKey(nextKey)) {
				nextValidKey = nextKey;
				break;
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {
		advanceIterator();
		return nextValidKey != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Map.Entry<K, V> next() {
		advanceIterator();
		if (nextValidKey != null) {
			previousValidKey = nextValidKey;
			nextValidKey = null;
			return new AbstractMap.SimpleEntry<>(
					previousValidKey, map.get(previousValidKey));
		} else {
			throw new NoSuchElementException();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove() {
		if (previousValidKey != null) {
			map.remove(previousValidKey);
			previousValidKey = null;
		} else {
			throw new IllegalStateException();
		}
	}
}

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

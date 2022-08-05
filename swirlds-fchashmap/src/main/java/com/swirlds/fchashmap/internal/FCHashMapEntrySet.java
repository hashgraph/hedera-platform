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

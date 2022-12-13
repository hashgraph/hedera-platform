/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.fchashmap.test;

import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fchashmap.internal.Mutation;
import java.util.Map;

/** Identical behavior as {@link FCHashMap} but with some protected methods made public. */
public class AccessibleFCHashMap<K, V> extends FCHashMap<K, V> {

    /** Create a new AccessibleFCHashMap. */
    public AccessibleFCHashMap() {
        super(0);
    }

    /**
     * Create a new AccessibleFCHashMap with an initial capacity.
     *
     * @param capacity the initial capacity
     */
    public AccessibleFCHashMap(final int capacity) {
        super(capacity);
    }

    /**
     * Copy constructor.
     *
     * @param that the map to copy
     */
    private AccessibleFCHashMap(final AccessibleFCHashMap<K, V> that) {
        super(that);
    }

    /** {@inheritDoc} */
    @Override
    public AccessibleFCHashMap<K, V> copy() {
        throwIfImmutable();
        throwIfDestroyed();
        return new AccessibleFCHashMap<>(this);
    }

    /** {@inheritDoc} */
    @Override
    public Map<K, Mutation<V>> getData() {
        return super.getData();
    }

    /** {@inheritDoc} */
    @Override
    public Mutation<V> getMutationForCurrentVersion(final K key) {
        return super.getMutationForCurrentVersion(key);
    }

    /** {@inheritDoc} */
    @Override
    public int copyCount() {
        return super.copyCount();
    }
}

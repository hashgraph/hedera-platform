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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.fchashmap.FCHashMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;

/**
 * A sequence of FCHashMap copies. Each copy is paired with a standard HashMap which is used to
 * validate the contents of each copy.
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public class FCHashMapSequence<K, V> {

    private final List<Pair<Map<K, V>, FCHashMap<K, V>>> copies;

    public FCHashMapSequence() {
        copies = new LinkedList<>();
        final AccessibleFCHashMap<K, V> map = new AccessibleFCHashMap<>();
        copies.add(Pair.of(new HashMap<>(), map));
    }

    /** Throw an exception if all copies have been released. */
    private void throwIfReleased() {
        assertFalse(
                copies.isEmpty(), "can not perform operations if all copies have been released");
    }

    /** Get the pair containing the mutable copy. */
    private Pair<Map<K, V>, FCHashMap<K, V>> getMutablePair() {
        return copies.get(copies.size() - 1);
    }

    /** Make a copy of the latest reference map. */
    private Map<K, V> makeReferenceCopy() {
        final Map<K, V> reference = getMutablePair().getLeft();

        final Map<K, V> referenceCopy = new HashMap<>();

        for (final Map.Entry<K, V> entry : reference.entrySet()) {
            referenceCopy.put(entry.getKey(), entry.getValue());
        }

        return referenceCopy;
    }

    /** Make a copy of the mutable FCHashMap. */
    private FCHashMap<K, V> makeFCHashMapCopy() {
        return getMutablePair().getRight().copy();
    }

    /** Make a copy of the map and its reference. */
    public void copy() {
        throwIfReleased();
        copies.add(Pair.of(makeReferenceCopy(), makeFCHashMapCopy()));
    }

    /**
     * Release a copy. The oldest copy in memory has index 0.
     *
     * @param index the index of the copy to release
     */
    public void release(final int index) {
        throwIfReleased();
        final FCHashMap<K, V> map = copies.get(index).getRight();
        if (!map.isImmutable()) {
            assertEquals(1, copies.size(), "mutable map must be released last");
        }
        map.release();
        copies.remove(index);
    }

    /**
     * Release on of the copies, chosen randomly. Mutable copy is never released as long as there is
     * at least one immutable copy.
     */
    public void releaseRandom(final Random random) {
        throwIfReleased();

        final int index;
        if (copies.size() <= 2) {
            index = 0;

        } else {
            index = random.nextInt(copies.size() - 2);
        }

        release(index);
    }

    /** Get the number of copies that have not been released, including the mutable copy. */
    public int getNumberOfCopies() {
        return copies.size();
    }

    /** Put a value into the map and its reference */
    public void put(final K key, final V value) {
        throwIfReleased();

        final Pair<Map<K, V>, FCHashMap<K, V>> pair = getMutablePair();
        final Map<K, V> reference = pair.getLeft();
        final FCHashMap<K, V> map = pair.getRight();

        final V previousReference = reference.put(key, value);
        final V previous = map.put(key, value);

        assertEquals(
                previousReference,
                previous,
                "map should have same previous value as reference map");
    }

    /** Remove a value from the map and its reference */
    public void remove(final K key) {
        throwIfReleased();

        final Pair<Map<K, V>, FCHashMap<K, V>> pair = getMutablePair();
        final Map<K, V> reference = pair.getLeft();
        final FCHashMap<K, V> map = pair.getRight();

        final V previousReference = reference.remove(key);
        final V previous = map.remove(key);

        assertEquals(
                previousReference,
                previous,
                "map should have same previous value as reference map");
    }

    /** Assert that a single copy is valid. */
    private void assertCopyValidity(final Pair<Map<K, V>, FCHashMap<K, V>> pair) {
        final Map<K, V> reference = pair.getLeft();
        final FCHashMap<K, V> map = pair.getRight();

        Assertions.assertEquals(
                reference.size(), map.size(), "size of map should match the reference");

        for (final Map.Entry<K, V> entry : reference.entrySet()) {
            Assertions.assertEquals(
                    entry.getValue(), map.get(entry.getKey()), "value should match reference map");
        }
    }

    /** Assert that all copies match their reference maps. */
    public void assertValidity() {
        for (final Pair<Map<K, V>, FCHashMap<K, V>> pair : copies) {
            assertCopyValidity(pair);
        }
    }

    /** Get the internal list of copies. */
    public List<Pair<Map<K, V>, FCHashMap<K, V>>> getCopies() {
        return copies;
    }
}

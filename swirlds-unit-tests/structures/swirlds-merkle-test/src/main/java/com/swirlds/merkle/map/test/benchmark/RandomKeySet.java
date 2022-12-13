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
package com.swirlds.merkle.map.test.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * A data structure to store keys that can be drawn randomly. This data structure should have the
 * following properties:
 *
 * <ul>
 *   <li>thread safe
 *   <li>O(1) insertion
 *   <li>O(1) deletion
 *   <li>O(1) time to get a random element.
 *   <li>It is assumed that the number of insertions and deletions are about the same and that the
 *       number of recently deleted items is small compared to the overall number of keys
 * </ul>
 */
public class RandomKeySet<T> {

    /**
     * The data currently in the KeyList. Will only increase in size, never decrease. When elements
     * are deleted the list is not reshuffled (costly!), instead a null is placed in the gap.
     */
    private final List<T> data;

    /**
     * As items are deleted, null "gaps" are placed into data. Store those gap indexes here. When
     * inserting a new elements first attempt to write into one of these gaps.
     */
    private Queue<Integer> gaps;

    /** The position within the data list of each key that is currently tracked. */
    private final HashMap<T, Integer> keyPositions;

    /** The number of keys. */
    private int keyCount;

    public RandomKeySet() {
        data = new ArrayList<>();
        gaps = new LinkedList<>();
        keyPositions = new HashMap<>();
    }

    /** Returns a key at a given index. May return null if there is no key at the index. */
    public synchronized T getRandomKey(final Random random) {
        if (keyCount == 0) {
            return null;
        }

        final int size = data.size();
        int index = random.nextInt(size);

        T key = data.get(index);
        while (key == null) {
            index = (index + 1) % size;
            key = data.get(index);
        }

        return data.get(index);
    }

    /** Add a new key. */
    public synchronized void add(T key) {
        if (keyPositions.containsKey(key)) {
            return;
        }

        if (key == null) {
            throw new IllegalArgumentException("null keys are not supported");
        }
        if (gaps.size() > 0) {
            // Add the data to a gap
            int index = gaps.remove();
            data.set(index, key);
            keyPositions.put(key, index);
        } else {
            // Add the data to the end, increasing the capacity
            data.add(key);
            keyPositions.put(key, data.size() - 1);
        }

        keyCount++;
    }

    /** Remove a key. */
    public synchronized void remove(final T key) {
        final Integer index = keyPositions.get(key);
        if (index == null) {
            return;
        }

        data.set(index, null);
        gaps.add(index);
        keyPositions.remove(key);
        keyCount--;
    }

    /** Get the number of keys currently in the set. */
    public synchronized int size() {
        return keyCount;
    }
}

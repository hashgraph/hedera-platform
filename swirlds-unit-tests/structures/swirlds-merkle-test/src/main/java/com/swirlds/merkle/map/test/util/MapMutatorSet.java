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
package com.swirlds.merkle.map.test.util;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.test.dummy.Key;
import com.swirlds.common.test.dummy.Value;
import com.swirlds.merkle.map.test.dummy.FCQValue;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * MapMutatorSet contains a set of methods for inserting leaves into a MerkleMap, updating a
 * percentage of leaves in a FCMap, or deleting a percentage of leaves from a MerkleMap. The Value
 * type of MerkleMap can be simple or FCQ
 */
public class MapMutatorSet {

    private static final Marker FCM_TEST = MarkerManager.getMarker("FCM_TEST");

    private static final Logger LOG = LogManager.getLogger(MapMutatorSet.class);

    public static final Random random = new Random();

    private final ValueType valueType;

    public enum ModifyType {
        UPDATE("Update"),
        DELETE("Delete");

        private final String name;

        ModifyType(final String name) {
            this.name = name;
        }
    }

    public enum ValueType {
        SIMPLE,
        FCQ,
        FCM
    }

    public MapMutatorSet(ValueType valueType) {
        this.valueType = valueType;
    }

    public <V extends MerkleNode> void insertIntoMap(
            final int startIndex, final int endIndex, final Map<Key, V> fcm) {

        switch (valueType) {
            case SIMPLE:
                insertKeyValueIntoMap(startIndex, endIndex, fcm);
                break;
            case FCQ:
                insertKeyFCQIntoMap(startIndex, endIndex, fcm);
                break;
            default:
                LOG.error(FCM_TEST, () -> "Invalid ValueType");
        }
    }

    @SuppressWarnings("unchecked")
    public static <V extends FastCopyable> void insertKeyFCQIntoMap(
            final int startIndex, final int endIndex, final Map<Key, V> fcm) {
        for (int index = startIndex; index < endIndex; index++) {
            final Key key = new Key(new long[] {index, index, index});
            final V value = (V) FCQValue.buildRandomWithIndex(index);
            fcm.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <V extends MerkleNode> void insertKeyValueIntoMap(
            final int startIndex, final int endIndex, final Map<Key, V> fcm) {
        final long startTime = System.nanoTime();
        for (int index = startIndex; index < endIndex; index++) {
            final Key key = new Key(new long[] {index, index, index});
            final V value = (V) new Value(index, index, index, true);
            fcm.put(key, value);
        }

        final long endTime = System.nanoTime();
        final long totalTime = (endTime - startTime) / 1_000_000;
        LOG.info(
                FCM_TEST,
                "Inserting from {} to {} elements into MerkleMap took {} milliseconds",
                startIndex,
                endIndex,
                totalTime);
    }

    /** Update or Delete values of a percentage of Keys in MerkleMap */
    @SuppressWarnings("unchecked")
    public <K, V extends FastCopyable> void modifyMap(
            final Map<K, V> fcm, final int percentage, final ModifyType modifyType) {
        // decide which keys to be updated or deleted
        Set<K> toBeModifiedKeys = new HashSet<>();
        for (Map.Entry<K, V> entry : fcm.entrySet()) {
            if (random.nextInt(100) < percentage) {
                toBeModifiedKeys.add(entry.getKey());
            }
        }

        // start updating or deleting
        final long startTime = System.nanoTime();
        for (K key : toBeModifiedKeys) {
            if (modifyType.equals(ModifyType.UPDATE)) {
                V value = null;
                switch (valueType) {
                    case SIMPLE:
                        value = (V) Value.buildRandomValue();
                        break;
                    case FCQ:
                        value = (V) FCQValue.buildRandom();
                        break;
                }
                fcm.put(key, value);
            } else {
                // Delete
                fcm.remove(key);
            }
        }
        final long endTime = System.nanoTime();
        final long totalTime = (endTime - startTime) / 1_000_000;
        LOG.info(
                FCM_TEST,
                "{} {} elements in MerkleMap (with {} elements) took {} milliseconds",
                modifyType,
                toBeModifiedKeys.size(),
                fcm.size(),
                totalTime);
    }

    /** Update values of a percentage of Keys in MerkleMap */
    public <K, V extends FastCopyable> void updateValueInMap(
            final Map<K, V> fcm, final int percentage) {
        modifyMap(fcm, percentage, ModifyType.UPDATE);
    }

    /** Delete a percentage of key and value from MerkleMap */
    public <K, V extends FastCopyable> void deleteFromMap(
            final Map<K, V> fcm, final int percentage) {
        modifyMap(fcm, percentage, ModifyType.DELETE);
    }
}

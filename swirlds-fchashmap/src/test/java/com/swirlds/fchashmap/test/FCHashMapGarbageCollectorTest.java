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

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.fchashmap.internal.Mutation;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("FCHashMap Garbage Collector Test")
class FCHashMapGarbageCollectorTest {

    /** Check all mutations in all copies, verify that each mutation is required to exist. */
    private void assertValidity(final FCHashMapSeries<Integer, Integer> copies) {
        final Map<Integer, Mutation<Integer>> data = copies.getLatest().getData();

        // Gather all existing mutations
        final Set<Mutation<Integer>> mutations = new HashSet<>();
        for (final int key : data.keySet()) {
            Mutation<Integer> mutation = data.get(key);
            assertNotNull(mutation, "all entries should have at least one mutation");
            while (mutation != null) {
                mutations.add(mutation);
                mutation = mutation.getPrevious();
            }
        }

        // Scan each copy of the map, removing the mutations we find from the mutation set
        for (final AccessibleFCHashMap<Integer, Integer> copy : copies) {
            for (final int key : data.keySet()) {
                final Mutation<Integer> mutation = copy.getMutationForCurrentVersion(key);
                if (mutation == null) {
                    assertNull(
                            copy.get(key),
                            "if there is no mutation then the map should contain null value");
                    continue;
                }

                // For the sake of sanity, ensure that the mutation reflects the value the map holds
                if (mutation.getValue() == null) {
                    assertNull(copy.get(key), "mutation is deleted, map should return null");
                } else {
                    assertEquals(
                            mutation.getValue(),
                            copy.get(key),
                            "map should contain value described by mutation");
                }

                mutations.remove(mutation);
            }
        }

        // There may still be some mutations remaining if copies are deleted out of order. This is
        // because
        // the garbage collector always deletes versions in order. Remove any mutation with a
        // version that is not
        // less than the next version that is to be garbage collected.
        long lowestVersion = Long.MAX_VALUE;
        for (final AccessibleFCHashMap<Integer, Integer> map : copies) {
            lowestVersion = Math.min(map.version(), lowestVersion);
        }
        final Iterator<Mutation<Integer>> iterator = mutations.iterator();
        while (iterator.hasNext()) {
            final Mutation<Integer> mutation = iterator.next();
            if (mutation.getVersion() >= lowestVersion) {
                iterator.remove();
            }
        }

        // Each mutation should be reachable from one of the copies, so the set should be empty
        assertTrue(mutations.isEmpty(), "all mutations should have been reachable");
    }

    @Test
    @Tag(TIME_CONSUMING)
    @DisplayName("Leak")
    void leak() {

        final int maxKey = 10_000;
        final int iterations = 1_000_000;
        final int operationsPerCopy = 1000;
        final int copiesToKeep = 10;
        final int operationsPerValidation = 100_000;

        final FCHashMapSeries<Integer, Integer> copies = new FCHashMapSeries<>();
        final Random random = getRandomPrintSeed();

        for (int iteration = 0; iteration < iterations; iteration++) {
            final AccessibleFCHashMap<Integer, Integer> map = copies.getLatest();

            // Insert a value
            map.put(random.nextInt(maxKey), random.nextInt());

            // Delete a value
            final int keyToDelete = random.nextInt(maxKey);
            if (copies.getLatest().containsKey(keyToDelete)) {
                map.remove(keyToDelete);
            }

            // Every Nth round make a copy
            if (iteration % operationsPerCopy == 0) {
                copies.copy();
                // If there are too many copies then delete one at random
                if (copies.getNumberOfCopies() > copiesToKeep) {
                    copies.delete(random);

                    assertEquals(
                            copies.copyCount(),
                            copies.getLatest().copyCount(),
                            "number of uncleaned copies does not match expected");
                }
            }

            // Every Nth round do validation
            if (iteration % operationsPerValidation == 0) {
                assertValidity(copies);
            }
        }

        System.out.println(
                "Random operations finished, deleting all remaining copies and validating");

        assertValidity(copies);

        while (copies.getNumberOfCopies() > 1) {
            copies.delete(random);
            assertValidity(copies);
        }

        copies.deleteMutableCopy();
    }
}

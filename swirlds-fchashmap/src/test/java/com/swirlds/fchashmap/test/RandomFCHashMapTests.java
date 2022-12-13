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

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Random FCHashMap Tests")
class RandomFCHashMapTests {

    @Test
    @DisplayName("Random Operations")
    void randomOperations() {

        final Random random = getRandomPrintSeed();

        final int iterations = 100_000;
        final int maxCopiesInMemory = 10;
        final int operationsPerCopy = 100;
        final int maxKey = 500;

        final FCHashMapSequence<Integer, Integer> sequence = new FCHashMapSequence<>();

        for (int iteration = 0; iteration < iterations; iteration++) {

            sequence.put(random.nextInt(maxKey), random.nextInt());
            sequence.remove(random.nextInt(maxKey));

            if (iteration % operationsPerCopy == 0) {
                sequence.copy();

                if (sequence.getNumberOfCopies() > maxCopiesInMemory) {
                    sequence.releaseRandom(random);
                }

                sequence.assertValidity();
            }
        }

        // Destroy all remaining copies
        while (sequence.getNumberOfCopies() > 0) {
            sequence.releaseRandom(random);
            sequence.assertValidity();
        }
    }
}

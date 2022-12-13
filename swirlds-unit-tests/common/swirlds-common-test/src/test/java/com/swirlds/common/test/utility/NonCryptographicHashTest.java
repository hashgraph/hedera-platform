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
package com.swirlds.common.test.utility;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static com.swirlds.common.utility.NonCryptographicHashing.hash64;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("ResultOfMethodCallIgnored")
@DisplayName("Non-Cryptographic Hash Test")
class NonCryptographicHashTest {

    /**
     * This test does not attempt to verify statistical properties of the hash functions. Its
     * purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash32")
    @Test
    void testHash32() {
        final Random random = getRandomPrintSeed();

        hash32(random.nextInt());
        hash32(random.nextInt(), random.nextInt());
        hash32(random.nextInt(), random.nextInt(), random.nextInt());
        hash32(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash32(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());

        hash32(random.nextLong());
        hash32(random.nextLong(), random.nextLong());
        hash32(random.nextLong(), random.nextLong(), random.nextLong());
        hash32(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash32(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
    }

    /**
     * This test does not attempt to verify statistical properties of the hash functions. Its
     * purpose is to ensure that none of the methods cause a crash.
     */
    @DisplayName("Test hash64")
    @Test
    void testHash64() {
        final Random random = getRandomPrintSeed();

        hash64(random.nextInt());
        hash64(random.nextInt(), random.nextInt());
        hash64(random.nextInt(), random.nextInt(), random.nextInt());
        hash64(random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());
        hash64(
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt(),
                random.nextInt());

        hash64(random.nextLong());
        hash64(random.nextLong(), random.nextLong());
        hash64(random.nextLong(), random.nextLong(), random.nextLong());
        hash64(random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
        hash64(
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                random.nextLong());
    }

    @DisplayName("Hashes Are Not Degenerate")
    @Test
    void hashesAreNonDegenerate() {
        assertNotEquals(0, hash32(1L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L, 4L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L, 4L, 5L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L, 4L, 5L, 6L), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1L, 2L, 3L, 4L, 5L, 6L, 7L), "Hashes should be non-degenerate");
        assertNotEquals(
                0, hash32(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), "Hashes should be non-degenerate");
        assertNotEquals(
                0, hash32(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L), "Hashes should be non-degenerate");
        assertNotEquals(
                0,
                hash32(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                "Hashes should be non-degenerate");
        assertNotEquals(
                0,
                hash32(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L),
                "Hashes should be non-degenerate");

        assertNotEquals(0, hash32(1), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4, 5), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4, 5, 6), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4, 5, 6, 7), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4, 5, 6, 7, 8), "Hashes should be non-degenerate");
        assertNotEquals(0, hash32(1, 2, 3, 4, 5, 6, 7, 8, 9), "Hashes should be non-degenerate");
        assertNotEquals(
                0, hash32(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), "Hashes should be non-degenerate");
        assertNotEquals(
                0, hash32(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), "Hashes should be non-degenerate");
    }
}

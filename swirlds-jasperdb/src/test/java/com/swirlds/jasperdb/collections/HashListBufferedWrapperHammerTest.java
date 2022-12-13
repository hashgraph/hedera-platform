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
package com.swirlds.jasperdb.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HashListBufferedWrapperHammerTest extends BufferedWrapperTestBase {
    private static final Cryptography CRYPTO = CryptoFactory.getInstance();
    private static final long ORIGINAL_MARKER = 9_000_000_000L;
    private static final long FIRST_UPDATE_MARKER = 1_000_000_000L;
    private static final long SECOND_UPDATE_MARKER = 2_000_000_000L;

    /**
     * A simple "HAMMER" test to verify that we do *NOT* have a race condition between the thread
     * that writes the overlay data to the main index and any writer threads that are writing during
     * the same period of time.
     *
     * @throws InterruptedException If this exception is thrown, something unexpectedly was
     *     interrupted. Shouldn't happen.
     */
    @ParameterizedTest
    @ValueSource(ints = {100, 10_000, 100_000})
    @Tags({@Tag(TestTypeTags.HAMMER)})
    @DisplayName("Hammer the overlay, writing at the same time it is updating the main index")
    void hammerTestOverlay(int maxValues) throws InterruptedException, IOException {
        // Set up the main index and wrapper
        final SlowHashList mainIndex = new SlowHashList(maxValues);
        final HashListBufferedWrapper wrapper = new HashListBufferedWrapper(mainIndex);

        // Create all values in the main index using the ORIGINAL_MARKER, so we can identify which
        // elements were never touched.
        for (int i = 0; i < maxValues; i++) {
            wrapper.put(i, hash(i + ORIGINAL_MARKER));
        }

        // Start overlay mode
        wrapper.setUseOverlay(true);

        // Write a new value for every element into the overlay with the FIRST_UPDATE_MARKER, so we
        // can
        // identify which elements in the main index were updated based on the cache *before* the
        // overlay
        // is fully flushed and disabled.
        for (int i = 0; i < maxValues; i++) {
            wrapper.put(i, hash(i + FIRST_UPDATE_MARKER));
        }

        // Spawn two threads. The first thread will close the overlay mode (and therefore start
        // writing
        // to the main index). The second thread will write to every other slot in the wrapper
        // concurrent
        // with the first thread. If everything works correctly, the underlying index should have
        // "i+2L" in the non-odd slots, and "i+3L" in the odd slots.
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(2);
        new CoordinatedThread(
                        startGate,
                        endGate,
                        () -> {
                            mainIndex.slowItDown();
                            wrapper.setUseOverlay(false);
                            endGate.countDown();
                        })
                .start();

        new CoordinatedThread(
                        startGate,
                        endGate,
                        () -> {
                            final var toModify =
                                    IntStream.range(1, maxValues)
                                            .filter(i -> i % 2 != 0)
                                            .boxed()
                                            .collect(Collectors.toList());

                            final Random rand = new Random(42);
                            Collections.shuffle(toModify, rand);

                            toModify.stream()
                                    .parallel()
                                    .forEach(
                                            i -> {
                                                wrapper.put(i, hash(i + SECOND_UPDATE_MARKER));
                                                waitABit();
                                            });
                        })
                .start();

        // Wait for the threads to finish
        startGate.countDown();
        assertTrue(
                endGate.await(5, TimeUnit.MINUTES),
                "Timed out waiting for the threads to finish working");

        // Check the results
        for (int i = 0; i < maxValues; i++) {
            // 0, 2, 4, ... should have received the first update but NOT the second update!
            final Hash value = wrapper.get(i);
            if (i % 2 == 0) {
                assertNotEquals(
                        hash(i + ORIGINAL_MARKER), value, "element " + i + " was never updated!");
                assertNotEquals(
                        hash(i + SECOND_UPDATE_MARKER),
                        value,
                        "element "
                                + i
                                + " was part of the second update but should not have been!");
                assertEquals(
                        hash(i + FIRST_UPDATE_MARKER),
                        value,
                        "element " + i + " had an unexpected value " + value);
            } else {
                assertNotEquals(
                        hash(i + ORIGINAL_MARKER), value, "element " + i + " was never updated!");
                assertNotEquals(
                        hash(i + FIRST_UPDATE_MARKER),
                        value,
                        "element " + i + " was supposed to be part of the second update!");
                assertEquals(
                        hash(i + SECOND_UPDATE_MARKER),
                        value,
                        "element " + i + " had an unexpected value " + value);
            }
        }
    }

    private static Hash hash(long value) {
        return CRYPTO.digestSync(("" + value).getBytes(StandardCharsets.UTF_8));
    }

    private static final class SlowHashList implements HashList {
        private final AtomicReferenceArray<Hash> arr;
        private final int maxSize;
        private boolean slow = false;

        public SlowHashList(final int maxSize) {
            this.arr = new AtomicReferenceArray<>(maxSize);
            this.maxSize = maxSize;
        }

        public void slowItDown() {
            this.slow = true;
        }

        @Override
        public void close() throws IOException {
            for (int i = 0; i < arr.length(); i++) {
                arr.set(i, null);
            }
        }

        @Override
        public Hash get(final long index) throws IOException {
            return arr.get((int) index);
        }

        @Override
        public void put(final long index, final Hash hash) {
            if (slow) {
                waitABit();
            }
            arr.set((int) index, hash);
        }

        @Override
        public long capacity() {
            return maxSize;
        }

        @Override
        public long size() {
            return arr.length();
        }

        @Override
        public long maxHashes() {
            return maxSize;
        }

        @Override
        public void writeToFile(final Path file) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}

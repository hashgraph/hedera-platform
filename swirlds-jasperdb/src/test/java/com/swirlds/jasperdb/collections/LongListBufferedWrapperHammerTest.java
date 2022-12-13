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

import com.swirlds.test.framework.TestTypeTags;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LongListBufferedWrapperHammerTest extends BufferedWrapperTestBase {
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
    void hammerTestOverlay(int maxValues) throws InterruptedException {
        // Set up the main index and wrapper
        final SlowLongList mainIndex = new SlowLongList(maxValues);
        final LongListBufferedWrapper wrapper = new LongListBufferedWrapper(mainIndex);

        // Create all values in the main index using the ORIGINAL_MARKER, so we can identify which
        // elements were never touched.
        for (int i = 0; i < maxValues; i++) {
            wrapper.put(i, i + ORIGINAL_MARKER);
        }

        // Start overlay mode
        wrapper.setUseOverlay(true);

        // Write a new value for every element into the overlay with the FIRST_UPDATE_MARKER, so we
        // can
        // identify which elements in the main index were updated based on the cache *before* the
        // overlay
        // is fully flushed and disabled.
        for (int i = 0; i < maxValues; i++) {
            wrapper.put(i, i + FIRST_UPDATE_MARKER);
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
                            mainIndex.setSlow(true);
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
                                                if (i % 3 == 0) {
                                                    wrapper.putIfEqual(
                                                            i,
                                                            i + FIRST_UPDATE_MARKER,
                                                            i + SECOND_UPDATE_MARKER);
                                                } else {
                                                    wrapper.put(i, i + SECOND_UPDATE_MARKER);
                                                }
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
        mainIndex.setSlow(false);
        for (int i = 0; i < maxValues; i++) {
            // 0, 2, 4, ... should have received the first update but NOT the second update!
            final long value = wrapper.get(i, -1);
            if (i % 2 == 0) {
                assertNotEquals(i + ORIGINAL_MARKER, value, "element " + i + " was never updated!");
                assertNotEquals(
                        i + SECOND_UPDATE_MARKER,
                        value,
                        "element "
                                + i
                                + " was part of the second update but should not have been!");
                assertEquals(
                        i + FIRST_UPDATE_MARKER,
                        value,
                        "element " + i + " had an unexpected value " + value);
            } else {
                assertNotEquals(i + ORIGINAL_MARKER, value, "element " + i + " was never updated!");
                assertNotEquals(
                        i + FIRST_UPDATE_MARKER,
                        value,
                        "element " + i + " was supposed to be part of the second update!");
                assertEquals(
                        i + SECOND_UPDATE_MARKER,
                        value,
                        "element " + i + " had an unexpected value " + value);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"put", "putIfEqual"})
    @Tags({@Tag(TestTypeTags.HAMMER)})
    @DisplayName("Hammer the overlay, writing at the same time overlay is switched on and off")
    void hammerTestOverlay2(String mode) {
        final int MAX_VALUES = 1000;
        final int ITERATIONS = 10;

        // Set up the main index and its wrapper
        final SlowLongList mainIndex = new SlowLongList(MAX_VALUES);
        final LongListBufferedWrapper wrapper = new LongListBufferedWrapper(mainIndex);
        for (int i = 0; i < MAX_VALUES; i++) {
            wrapper.put(i, 1);
        }

        // Run a thread switching overlay on and off until updates finish
        final AtomicBoolean done = new AtomicBoolean(false);
        Thread overlay =
                new Thread(
                        () -> {
                            while (!done.get()) {
                                wrapper.setUseOverlay(true);
                                waitABit();
                                wrapper.setUseOverlay(false);
                                waitABit();
                            }
                        });
        overlay.start();

        // Pick an update function
        final BiConsumer<Integer, Integer> update;
        if (mode.equals("put")) {
            update = (v, i) -> wrapper.put(i, wrapper.get(i, -1) + 1);
        } else {
            update = (v, i) -> wrapper.putIfEqual(i, v, v + 1);
        }

        // Run parallel updates
        mainIndex.setSlow(true);
        final int numThreads = Runtime.getRuntime().availableProcessors();
        for (int iter = 0; iter < ITERATIONS; ++iter) {
            final AtomicInteger taskId = new AtomicInteger(0);
            final int curValue = iter + 1;
            IntStream.range(0, numThreads)
                    .parallel()
                    .forEach(
                            (t) -> {
                                for (; ; ) {
                                    final int task = taskId.getAndIncrement();
                                    if (task >= MAX_VALUES) break;
                                    update.accept(curValue, task);
                                }
                            });
        }
        done.set(true);

        mainIndex.setSlow(false);
        for (int i = 0; i < MAX_VALUES; i++) {
            final long value = wrapper.get(i, -1);
            assertEquals(
                    ITERATIONS + 1, value, "element " + i + " had an unexpected value " + value);
        }
    }

    protected static final class SlowLongList extends LongList {
        private final AtomicLongArray arr;
        private boolean slow = false;

        public SlowLongList(int maxValues) {
            super(maxValues, maxValues);
            this.arr = new AtomicLongArray(maxValues);
        }

        public void setSlow(boolean value) {
            this.slow = value;
        }

        @Override
        public void put(final long index, final long value) {
            if (slow) {
                waitABit();
            }
            arr.set((int) index, value);
            super.size.updateAndGet(currentValue -> Math.max(currentValue, index + 1));
        }

        @Override
        public boolean putIfEqual(final long index, final long oldValue, final long newValue) {
            return arr.compareAndSet((int) index, oldValue, newValue);
        }

        @Override
        protected void writeLongsData(final FileChannel fc) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        protected long lookupInChunk(final long chunkIndex, final long subIndex) {
            if (slow) {
                waitABit();
            }
            return arr.get((int) subIndex);
        }
    }
}

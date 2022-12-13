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

import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;
import static com.swirlds.jasperdb.collections.LongList.DEFAULT_MAX_LONGS_TO_STORE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LongListBufferedWrapperTest extends LongListHeapTest {
    private static LongListHeap longListHeap;
    private static LongListBufferedWrapper bufferedLongListWrapper;

    @Override
    protected LongList createLongList() {
        return new LongListBufferedWrapper(new LongListOffHeap());
    }

    @Override
    protected LongList createLongListWithChunkSizeInMb(final int chunkSizeInMb) {
        final int impliedLongsPerChunk =
                Math.toIntExact((((long) chunkSizeInMb * MEBIBYTES_TO_BYTES) / Long.BYTES));
        return new LongListOffHeap(impliedLongsPerChunk, DEFAULT_MAX_LONGS_TO_STORE);
    }

    @Override
    protected LongList createFullyParameterizedLongListWith(
            final int numLongsPerChunk, final long maxLongs) {
        return new LongListBufferedWrapper(new LongListOffHeap(numLongsPerChunk, maxLongs));
    }

    @Override
    protected LongList createLongListFromFile(final Path file) throws IOException {
        var longList = new LongListOffHeap(file);
        System.out.println("longList = " + longList);
        System.out.println("longList.size() = " + longList.size());
        System.out.println("file = " + file);
        System.out.println("Files.size(file) = " + Files.size(file));
        return new LongListBufferedWrapper(longList);
    }

    @Test
    @Order(100)
    void createDataWrapper() {
        // create list and wrapper
        longListHeap = new LongListHeap(1000, 100_000);
        bufferedLongListWrapper = new LongListBufferedWrapper(longListHeap);
        // fill with some data
        for (int i = 0; i < 2000; i++) {
            bufferedLongListWrapper.put(i, i + 10);
        }
        // check data
        for (int i = 0; i < 2000; i++) {
            assertEquals(
                    i + 10,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get()");
        }
        assertEquals(
                -1,
                bufferedLongListWrapper.get(2001, -1),
                "Unexpected value for bufferedLongListWrapper.get(2001)");
        assertEquals(
                -1,
                bufferedLongListWrapper.get(10_000, -1),
                "Unexpected value for bufferedLongListWrapper.get(10000)");
        assertEquals(
                2000,
                bufferedLongListWrapper.size(),
                "Unexpected value for bufferedLongListWrapper.size()");
    }

    @Test
    @Order(101)
    void checkLookupInChunk() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> bufferedLongListWrapper.lookupInChunk(0, 0),
                "Expected UnsupportedOperationException");
    }

    @Test
    @Order(102)
    void switchToOverlayModeAndCheckDataWrapper() {
        bufferedLongListWrapper.setUseOverlay(true);
        // check a second call
        bufferedLongListWrapper.setUseOverlay(true);
        // check data
        for (int i = 0; i < 2000; i++) {
            assertEquals(
                    i + 10,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get()");
        }
        assertEquals(
                -1,
                bufferedLongListWrapper.get(2001, -1),
                "Unexpected value for bufferedLongListWrapper.get(2000)");
        assertEquals(
                -1,
                bufferedLongListWrapper.get(10_000, -1),
                "Unexpected value for bufferedLongListWrapper.get(10000)");
        assertEquals(
                2000,
                bufferedLongListWrapper.size(),
                "Unexpected value for bufferedLongListWrapper.size()");
    }

    @Test
    @Order(103)
    void addSomeDataToOverlayWrapper() {
        // check put if equal
        bufferedLongListWrapper.putIfEqual(1000, 1010, 1100);
        // fill with some data
        for (int i = 1001; i < 3000; i++) {
            bufferedLongListWrapper.put(i, i + 100);
        }

        // check combined data
        for (int i = 0; i < 1000; i++) {
            assertEquals(
                    i + 10,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get()");
        }
        for (int i = 1000; i < 3000; i++) {
            assertEquals(
                    i + 100,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get()");
        }
        assertEquals(
                -1,
                bufferedLongListWrapper.get(3001, -1),
                "Unexpected value for bufferedLongListWrapper.get(3001)");
        assertEquals(
                3000,
                bufferedLongListWrapper.size(),
                "Unexpected value for bufferedLongListWrapper.size()");
        // check original list still has old data
        for (int i = 0; i < 2000; i++) {
            assertEquals(
                    i + 10,
                    longListHeap.get(i, -1),
                    "Unexpected value for longListHeap.get(" + i + ")");
        }
        assertEquals(-1, longListHeap.get(2001, -1), "Unexpected value for longListHeap.get(2001)");
    }

    @Test
    @Order(104)
    void endOverlayWrapper() {
        System.out.println("About to start setUseOverlay");
        bufferedLongListWrapper.setUseOverlay(false);
        System.out.println("done setUseOverlay");
        // check combined data
        for (int i = 0; i < 1000; i++) {
            assertEquals(
                    i + 10,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get(" + i + ")");
        }
        for (int i = 1000; i < 3000; i++) {
            assertEquals(
                    i + 100,
                    bufferedLongListWrapper.get(i, -1),
                    "Unexpected value for bufferedLongListWrapper.get(" + i + ")");
        }
        assertEquals(
                -1,
                bufferedLongListWrapper.get(3001, -1),
                "Unexpected value for bufferedLongListWrapper.get(3001)");
        // check wrapped list has all data now
        for (int i = 0; i < 1000; i++) {
            assertEquals(
                    i + 10,
                    longListHeap.get(i, -1),
                    "Unexpected value for longListHeap.get(" + i + ")");
        }
        for (int i = 1000; i < 3000; i++) {
            assertEquals(
                    i + 100,
                    longListHeap.get(i, -1),
                    "Unexpected value for longListHeap.get(" + i + ")");
        }
        assertEquals(-1, longListHeap.get(3001, -1), "Unexpected value for longListHeap.get(3001)");
    }

    @Test
    @Order(105)
    void checkWithThreadsWrapper() {
        // clean data
        for (int i = 0; i < 3000; i++) {
            bufferedLongListWrapper.put(i, i + 10);
        }
        // start checking in two threads
        AtomicBoolean doneChanging = new AtomicBoolean(false);
        IntStream.range(0, 2)
                .parallel()
                .forEach(
                        thread -> {
                            if (thread == 0) {
                                // keep checking thread
                                while (!doneChanging.get()) {
                                    for (int i = 0; i < 3000; i++) {
                                        assertEquals(
                                                i + 10,
                                                longListHeap.get(i, -1),
                                                "Unexpected value for longListHeap.get(" + i + ")");
                                    }
                                }
                            } else {
                                try {
                                    MILLISECONDS.sleep(30);
                                    bufferedLongListWrapper.setUseOverlay(true);
                                    MILLISECONDS.sleep(30);
                                    // change all the data with lots of threads
                                    IntStream.range(0, 3000)
                                            .parallel()
                                            .forEach(i -> bufferedLongListWrapper.put(i, i + 100));
                                    MILLISECONDS.sleep(30);
                                    // check new data
                                    for (int i = 0; i < 3000; i++) {
                                        assertEquals(
                                                i + 100,
                                                bufferedLongListWrapper.get(i, -1),
                                                "Unexpected value for bufferedLongListWrapper.get("
                                                        + i
                                                        + ")");
                                    }
                                    // done
                                    doneChanging.set(true);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
        // remove overlay while writing from other threads and check data is copied down
        AtomicBoolean doneSettingOverlay = new AtomicBoolean(false);
        doneChanging.set(false);
        IntStream.range(0, 3)
                .parallel()
                .forEach(
                        thread -> {
                            if (thread == 0) {
                                // keep checking thread
                                while (!doneChanging.get() && !doneSettingOverlay.get()) {
                                    for (int i = 0; i < 3000; i++) {
                                        assertEquals(
                                                i + 100,
                                                bufferedLongListWrapper.get(i, -1),
                                                "Unexpected value for bufferedLongListWrapper.get("
                                                        + i
                                                        + ")");
                                    }
                                }
                            } else if (thread == 1) {
                                // write new data thread, new data should end up in overlay or base
                                // but should all be written correctly
                                while (!doneSettingOverlay.get()) {
                                    IntStream.range(3000, 4000)
                                            .parallel()
                                            .forEach(i -> bufferedLongListWrapper.put(i, i + 1000));
                                }
                                // done
                                doneChanging.set(true);
                            } else {
                                try {
                                    MILLISECONDS.sleep(20);
                                    System.out.println("setUseOverlay");
                                    bufferedLongListWrapper.setUseOverlay(false);
                                    // done
                                    doneSettingOverlay.set(true);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
        // check we have all the correct data now in wrapped long list
        for (int i = 0; i < 3000; i++) {
            assertEquals(
                    i + 100,
                    longListHeap.get(i, -1),
                    "Unexpected value for longListHeap.get(" + i + ")");
        }
        for (int i = 3000; i < 4000; i++) {
            assertEquals(
                    i + 1000,
                    longListHeap.get(i, -1),
                    "Unexpected value for longListHeap.get(" + i + ")");
        }
    }
}

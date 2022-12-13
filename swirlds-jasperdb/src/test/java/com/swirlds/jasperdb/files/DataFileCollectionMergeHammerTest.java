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
package com.swirlds.jasperdb.files;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.jasperdb.collections.LongListHeap;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Hammer the merging subsystem with as many small merges as possible to try to overwhelm it. */
@Disabled
class DataFileCollectionMergeHammerTest {

    /** Temporary directory provided by JUnit */
    @SuppressWarnings("unused")
    @TempDir
    Path tempFileDir;

    @ParameterizedTest
    @MethodSource("provideForBenchmark")
    @Tags({@Tag("Speed")})
    void benchmark(int numFiles, int maxEntriesPerFile) {
        assertDoesNotThrow(
                () -> {
                    final LongListHeap index = new LongListHeap();
                    final var serializer = new ExampleFixedSizeDataSerializer();
                    final var coll =
                            new DataFileCollection<>(
                                    tempFileDir.resolve("mergeBenchTest"),
                                    "mergeBenchTest",
                                    serializer,
                                    (key, dataLocation, dataValue) -> {});

                    final Random rand = new Random(777);
                    final Semaphore pauseMerging = new Semaphore(1);
                    for (int i = 0; i < numFiles; i++) {
                        coll.startWriting();
                        final int numRecords = rand.nextInt(maxEntriesPerFile);
                        long prevId = 0;
                        for (int j = 0; j < numRecords; j++) {
                            final long id =
                                    prevId + rand.nextInt((maxEntriesPerFile * 10) - (int) prevId);
                            if (id == prevId) {
                                break;
                            }
                            prevId = id;
                            index.put(id, coll.storeDataItem(new long[] {id, rand.nextLong()}));
                        }
                        coll.endWriting(index.size() * 2L - 1, index.size() * 2L)
                                .setFileAvailableForMerging(true);
                    }

                    final long start = System.currentTimeMillis();
                    final var filesToMerge = coll.getAllFilesAvailableForMerge();
                    coll.mergeFiles(index, filesToMerge, pauseMerging);
                    System.out.println(
                            numFiles
                                    + " files took "
                                    + (System.currentTimeMillis() - start)
                                    + "ms");
                    index.close();
                });
    }

    private static Stream<Arguments> provideForBenchmark() {
        return Stream.of(
                Arguments.of(2, 100),
                Arguments.of(2, 1000),
                Arguments.of(2, 10_000),
                Arguments.of(2, 100_000),
                Arguments.of(10, 100),
                Arguments.of(10, 1000),
                Arguments.of(10, 10_000),
                Arguments.of(10, 100_000),
                Arguments.of(100, 100),
                Arguments.of(100, 1000),
                Arguments.of(100, 10_000),
                Arguments.of(100, 100_000),
                Arguments.of(1000, 100),
                Arguments.of(1000, 1000),
                Arguments.of(1000, 10_000),
                Arguments.of(1000, 100_000),
                Arguments.of(1000, 1_000_000));
    }

    @Test
    @Tags({@Tag(TestTypeTags.HAMMER)})
    void hammer() throws IOException, InterruptedException {
        final LongListHeap index = new LongListHeap();
        final var serializer = new ExampleFixedSizeDataSerializer();
        final var coll =
                new DataFileCollection<>(
                        tempFileDir.resolve("mergeHammerTest"),
                        "mergeHammerTest",
                        serializer,
                        (key, dataLocation, dataValue) -> {});

        final Random rand = new Random(777);
        final Semaphore pauseMerging = new Semaphore(1);
        final AtomicBoolean stop = new AtomicBoolean(false);
        new Thread(
                        () -> {
                            while (!stop.get()) {
                                try {
                                    coll.startWriting();
                                    final int numRecords = rand.nextInt(2500);
                                    long prevId = 0;
                                    for (int i = 0; i < numRecords; i++) {
                                        final long id =
                                                prevId + rand.nextInt((10_000) - (int) prevId);
                                        if (id == prevId) {
                                            break;
                                        }
                                        prevId = id;
                                        index.put(
                                                id,
                                                coll.storeDataItem(
                                                        new long[] {id, rand.nextLong()}));
                                    }
                                    coll.endWriting(index.size() * 2L - 1, index.size() * 2L)
                                            .setFileAvailableForMerging(true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                .start();

        new Thread(
                        () -> {
                            while (!stop.get()) {
                                try {
                                    final var filesToMerge = coll.getAllFilesAvailableForMerge();
                                    System.out.println(filesToMerge.size());
                                    if (filesToMerge.size() > 10000) {
                                        stop.set(true);
                                    }
                                    coll.mergeFiles(index, filesToMerge, pauseMerging);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        })
                .start();

        for (int i = 0; i < 100; i++) {
            pauseMerging.acquire();
            SECONDS.sleep(3);
            pauseMerging.release();
            SECONDS.sleep(1);
        }

        stop.set(true);
        final var filesToMerge = coll.getAllFilesAvailableForMerge();
        assertTrue(filesToMerge.size() < 10000, "Too many files! We didn't keep up");
        index.close();
    }
}

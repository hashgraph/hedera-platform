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
package com.swirlds.benchmark;

import com.swirlds.jasperdb.collections.LongListOffHeap;
import com.swirlds.jasperdb.files.MemoryIndexDiskKeyValueStore;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public class KeyValueStoreBench extends BaseBench {

    String benchmarkName() {
        return "KeyValueStoreBench";
    }

    @Benchmark
    public void merge() throws Exception {
        beforeTest("mergeBench");

        final BenchmarkRecord[] map = new BenchmarkRecord[verify ? maxKey : 0];
        final var store =
                new MemoryIndexDiskKeyValueStore<>(
                        getTestDir(),
                        "mergeBench",
                        new BenchmarkRecordSerializer(),
                        (key, dataLocation, dataValue) -> {},
                        new LongListOffHeap());
        System.out.println();

        // Write files
        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {
            store.startWriting();
            resetKeys();
            for (int j = 0; j < numRecords; ++j) {
                long id = nextAscKey();
                BenchmarkRecord value = new BenchmarkRecord(id, nextValue());
                store.put(id, value);
                if (verify) map[(int) id] = value;
            }
            store.endWriting(0, maxKey);
        }
        System.out.println(
                "Created " + numFiles + " files in " + (System.currentTimeMillis() - start) + "ms");

        // Merge files
        start = System.currentTimeMillis();
        final Semaphore pauseMerging = new Semaphore(1);
        final AtomicLong count = new AtomicLong(0);
        store.merge(
                list -> {
                    count.set(list.size());
                    return list;
                },
                pauseMerging,
                2);
        System.out.println(
                "Merged "
                        + count.get()
                        + " files in "
                        + (System.currentTimeMillis() - start)
                        + "ms");

        // Verify merged content
        if (verify) {
            start = System.currentTimeMillis();
            for (int key = 0; key < map.length; ++key) {
                BenchmarkRecord dataItem = store.get(key);
                if (dataItem == null) {
                    if (map[key] != null) {
                        throw new RuntimeException("Missing value");
                    }
                } else if (!dataItem.equals(map[key])) {
                    throw new RuntimeException("Bad value");
                }
            }
            System.out.println(
                    "Verified key-value store in " + (System.currentTimeMillis() - start) + "ms");
        }

        afterTest(store::close);
    }
}

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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.pipeline.VirtualRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
public class VirtualMapBench extends BaseBench {

    private static final Logger LOG = LogManager.getLogger(VirtualMapBench.class);

    private static final String LABEL = "vm";
    private static final String SAVED = "saved";
    private static final String SERDE = LABEL + ".serde";
    private static final String SNAPSHOT = "snapshot";
    private static final long SNAPSHOT_DELAY = 60_000;

    String benchmarkName() {
        return "VirtualMapBench";
    }

    /* This map may be pre-created on demand and reused between benchmarks/iterations */
    private VirtualMap<BenchmarkKey, BenchmarkValue> virtualMapP;
    /* Run snapshots periodically */
    private boolean doSnapshots;
    private final AtomicLong snapshotTime = new AtomicLong(0L);
    /* Asynchronous hasher */
    private final ExecutorService hasher =
            Executors.newSingleThreadExecutor(
                    new ThreadConfiguration()
                            .setComponent("benchmark")
                            .setThreadName("hasher")
                            .setExceptionHandler(
                                    (t, ex) -> LOG.error("Uncaught exception during hashing", ex))
                            .buildFactory());

    @TearDown
    public void destroyLocal() throws IOException {
        if (virtualMapP != null) {
            virtualMapP.release();
            virtualMapP.getDataSource().close();
            virtualMapP = null;
        }
        hasher.shutdown();
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createMap() {
        return createMap(null);
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> createMap(final long[] map) {

        final long start = System.currentTimeMillis();
        final VirtualMap<BenchmarkKey, BenchmarkValue> restoredMap = restoreMap();
        if (restoredMap != null) {
            if (verify && map != null) {
                final int parallelism = ForkJoinPool.getCommonPoolParallelism();
                final AtomicLong numKeys = new AtomicLong();
                IntStream.range(0, parallelism)
                        .parallel()
                        .forEach(
                                idx -> {
                                    long count = 0L;
                                    for (int i = idx; i < map.length; i += parallelism) {
                                        final BenchmarkValue value =
                                                restoredMap.get(new BenchmarkKey(i));
                                        if (value != null) {
                                            map[i] = value.toLong();
                                            ++count;
                                        }
                                    }
                                    numKeys.addAndGet(count);
                                });
                LOG.info("Loaded {} keys in {} ms", numKeys, System.currentTimeMillis() - start);
            } else {
                LOG.info("Loaded map in {} ms", System.currentTimeMillis() - start);
            }

            BenchmarkMetrics.register(restoredMap::registerMetrics);
            return restoredMap;
        }

        final VirtualLeafRecordSerializer<BenchmarkKey, BenchmarkValue>
                virtualLeafRecordSerializer =
                        new VirtualLeafRecordSerializer<>(
                                (short) 1,
                                DigestType.SHA_384,
                                (short) 1,
                                BenchmarkKey.getSerializedSize(),
                                new BenchmarkKeySupplier(),
                                (short) 1,
                                BenchmarkValue.getSerializedSize(),
                                new BenchmarkValueSupplier(),
                                true);

        final JasperDbBuilder<BenchmarkKey, BenchmarkValue> diskDbBuilder = new JasperDbBuilder<>();
        diskDbBuilder
                .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                .keySerializer(new BenchmarkKeySerializer())
                .storageDir(getTestDir())
                .preferDiskBasedIndexes(false);
        final VirtualMap<BenchmarkKey, BenchmarkValue> createdMap =
                new VirtualMap<>(LABEL, diskDbBuilder);
        BenchmarkMetrics.register(createdMap::registerMetrics);
        return createdMap;
    }

    private void enableSnapshots() {
        snapshotTime.set(System.currentTimeMillis() + SNAPSHOT_DELAY);
        doSnapshots = true;
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> copyMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final VirtualRoot root = virtualMap.getRight();
        final VirtualMap<BenchmarkKey, BenchmarkValue> newCopy = virtualMap.copy();
        hasher.execute(root::getHash);

        if (doSnapshots && System.currentTimeMillis() > snapshotTime.get()) {
            snapshotTime.set(Long.MAX_VALUE);
            new Thread(
                            () -> {
                                try {
                                    Path savedDir = getTestDir().resolve(SNAPSHOT);
                                    if (!Files.exists(savedDir)) {
                                        Files.createDirectory(savedDir);
                                    }
                                    virtualMap.getRight().getHash();
                                    try (final SerializableDataOutputStream out =
                                            new SerializableDataOutputStream(
                                                    Files.newOutputStream(
                                                            savedDir.resolve(SERDE)))) {
                                        virtualMap.serialize(out, savedDir);
                                    }
                                    virtualMap.release();
                                    Utils.deleteRecursively(savedDir);

                                    snapshotTime.set(System.currentTimeMillis() + SNAPSHOT_DELAY);
                                } catch (Exception ex) {
                                    LOG.error("Failed to take a snapshot", ex);
                                }
                            })
                    .start();
        } else {
            virtualMap.release();
        }

        return newCopy;
    }

    /*
     * Ensure map is fully flushed to disk. Save map to disk if saving data is specified.
     */
    protected VirtualMap<BenchmarkKey, BenchmarkValue> flushMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final long start = System.currentTimeMillis();
        VirtualMap<BenchmarkKey, BenchmarkValue> curMap = virtualMap;
        for (; ; ) {
            final VirtualMap<BenchmarkKey, BenchmarkValue> oldCopy = curMap;
            curMap = curMap.copy();
            oldCopy.release();
            final VirtualRoot root = oldCopy.getRight();
            if (root.shouldBeFlushed()) {
                try {
                    root.waitUntilFlushed();
                } catch (InterruptedException ex) {
                    LOG.warn("Interrupted", ex);
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
        LOG.info("Flushed map in {} ms", System.currentTimeMillis() - start);

        if (getConfig().saveDataDirectory()) {
            curMap = saveMap(curMap);
        }

        return curMap;
    }

    protected void verifyMap(long[] map, VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        if (!verify) {
            return;
        }

        long start = System.currentTimeMillis();
        final AtomicInteger index = new AtomicInteger(0);
        final AtomicInteger countGood = new AtomicInteger(0);
        final AtomicInteger countBad = new AtomicInteger(0);
        final AtomicInteger countMissing = new AtomicInteger(0);

        IntStream.range(0, ForkJoinPool.getCommonPoolParallelism())
                .parallel()
                .forEach(
                        thread -> {
                            int idx;
                            while ((idx = index.getAndIncrement()) < map.length) {
                                BenchmarkValue dataItem = virtualMap.get(new BenchmarkKey(idx));
                                if (dataItem == null) {
                                    if (map[idx] != 0L) {
                                        countMissing.getAndIncrement();
                                    }
                                } else if (!dataItem.equals(new BenchmarkValue(map[idx]))) {
                                    countBad.getAndIncrement();
                                } else {
                                    countGood.getAndIncrement();
                                }
                            }
                        });
        if (countBad.get() != 0 || countMissing.get() != 0) {
            LOG.error(
                    "FAIL verified {} keys, {} bad, {} missing in {} ms",
                    countGood.get(),
                    countBad.get(),
                    countMissing.get(),
                    System.currentTimeMillis() - start);
        } else {
            LOG.info(
                    "PASS verified {} keys in {} ms",
                    countGood.get(),
                    System.currentTimeMillis() - start);
        }
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> saveMap(
            final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap) {
        final VirtualMap<BenchmarkKey, BenchmarkValue> curMap = virtualMap.copy();
        try {
            final long start = System.currentTimeMillis();
            final Path savedDir = getBenchDir().resolve(SAVED).resolve(LABEL);
            if (Files.exists(savedDir)) {
                Utils.deleteRecursively(savedDir);
            }
            Files.createDirectories(savedDir);
            virtualMap.getRight().getHash();
            try (final SerializableDataOutputStream out =
                    new SerializableDataOutputStream(
                            Files.newOutputStream(savedDir.resolve(SERDE)))) {
                virtualMap.serialize(out, savedDir);
            }
            LOG.info("Saved map in {} ms", System.currentTimeMillis() - start);
        } catch (IOException ex) {
            LOG.error("Error saving VirtualMap", ex);
        } finally {
            virtualMap.release();
        }
        return curMap;
    }

    protected VirtualMap<BenchmarkKey, BenchmarkValue> restoreMap() {
        final Path savedDir = getBenchDir().resolve(SAVED).resolve(LABEL);
        if (Files.exists(savedDir)) {
            try {
                final VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = new VirtualMap<>();
                try (final SerializableDataInputStream in =
                        new SerializableDataInputStream(
                                Files.newInputStream(savedDir.resolve(SERDE)))) {
                    virtualMap.deserialize(in, savedDir, virtualMap.getVersion());
                }
                return virtualMap;
            } catch (IOException ex) {
                LOG.error("Error loading saved map: {}", ex.getMessage());
            }
        }
        return null;
    }

    /** [Read-update or create-write] cycle. Single-threaded. */
    @Benchmark
    public void update() throws Exception {
        beforeTest("update");

        LOG.info(RUN_DELIMITER);

        final long[] map = new long[verify ? maxKey : 0];
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = createMap(map);

        // Update values
        long start = System.currentTimeMillis();
        enableSnapshots();
        for (int i = 0; i < numFiles; i++) {

            for (int j = 0; j < numRecords; ++j) {
                long id = Utils.randomLong(maxKey);
                BenchmarkKey key = new BenchmarkKey(id);
                BenchmarkValue value = virtualMap.getForModify(key);
                long val = nextValue();
                if (value != null) {
                    if ((val & 0xff) == 0) {
                        virtualMap.remove(key);
                        if (verify) map[(int) id] = 0L;
                    } else {
                        value.update(l -> l + val);
                        if (verify) map[(int) id] += val;
                    }
                } else {
                    value = new BenchmarkValue(val);
                    virtualMap.put(key, value);
                    if (verify) map[(int) id] = val;
                }
            }

            virtualMap = copyMap(virtualMap);
        }

        LOG.info("Updated {} copies in {} ms", numFiles, System.currentTimeMillis() - start);

        // Ensure the map is done with hashing/merging/flushing
        final var finalMap = flushMap(virtualMap);

        verifyMap(map, finalMap);

        afterTest(
                () -> {
                    finalMap.release();
                    finalMap.getDataSource().close();
                });
    }

    /** [Create-write or replace] cycle. Single-threaded. */
    @Benchmark
    public void create() throws Exception {
        beforeTest("create");

        LOG.info(RUN_DELIMITER);

        final long[] map = new long[verify ? maxKey : 0];
        VirtualMap<BenchmarkKey, BenchmarkValue> virtualMap = createMap(map);

        // Write files
        long start = System.currentTimeMillis();
        for (int i = 0; i < numFiles; i++) {
            for (int j = 0; j < numRecords; ++j) {
                long id = Utils.randomLong(maxKey);
                final BenchmarkKey key = new BenchmarkKey(id);
                final long val = nextValue();
                final BenchmarkValue value = new BenchmarkValue(val);
                virtualMap.put(key, value);
                if (verify) {
                    map[(int) id] = val;
                }
            }

            virtualMap = copyMap(virtualMap);
        }

        LOG.info("Created {} copies in {} ms", numFiles, System.currentTimeMillis() - start);

        // Ensure the map is done with hashing/merging/flushing
        final var finalMap = flushMap(virtualMap);

        verifyMap(map, finalMap);

        afterTest(
                () -> {
                    finalMap.release();
                    finalMap.getDataSource().close();
                });
    }

    private void preCreateMap() {
        if (virtualMapP != null) return;
        virtualMapP = createMap();

        long start = System.currentTimeMillis();
        int count = 0;
        for (int i = 0; i < maxKey; i++) {
            BenchmarkKey key = new BenchmarkKey(i);
            BenchmarkValue value = new BenchmarkValue(nextValue());
            virtualMapP.put(key, value);

            if (++count == maxKey / numFiles) {
                count = 0;
                virtualMapP = copyMap(virtualMapP);
            }
        }

        LOG.info("Pre-created {} records in {} ms", maxKey, System.currentTimeMillis() - start);

        virtualMapP = flushMap(virtualMapP);
    }

    /** Read from a pre-created map. Parallel. */
    @Benchmark
    public void read() throws Exception {
        beforeTest("read");

        LOG.info(RUN_DELIMITER);

        preCreateMap();

        final long start = System.currentTimeMillis();
        final AtomicLong total = new AtomicLong(0);
        IntStream.range(0, numThreads)
                .parallel()
                .forEach(
                        thread -> {
                            long sum = 0;
                            for (int i = 0; i < numRecords; ++i) {
                                final long id = Utils.randomLong(maxKey);
                                BenchmarkValue value = virtualMapP.get(new BenchmarkKey(id));
                                sum += value.hashCode();
                            }
                            total.addAndGet(sum);
                        });

        LOG.info(
                "Read {} records from {} threads in {} ms",
                (long) numRecords * numThreads,
                numThreads,
                System.currentTimeMillis() - start);

        afterTest(true);
    }
}

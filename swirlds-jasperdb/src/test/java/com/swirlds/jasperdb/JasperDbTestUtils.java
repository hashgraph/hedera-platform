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
package com.swirlds.jasperdb;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.utility.Units;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.management.MBeanServer;

@SuppressWarnings("unused")
public class JasperDbTestUtils {
    /**
     * The amount of direct memory used by JVM and caches. This needs to be big enough to allow for
     * variations in test runs while being small enough to catch leaks in tests.
     */
    private static final long DIRECT_MEMORY_BASE_USAGE = 4 * Units.MEBIBYTES_TO_BYTES;

    /**
     * Run a callable test in the background and then make sure no direct memory is leaked and not
     * databases are left open. Running test in a thread helps by allowing the thread to be killed
     * so we can clean up any thread local cached data used in the test.
     *
     * @param callable The test to run
     * @throws Exception If there was a problem running the test
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void runTaskAndCleanThreadLocals(Callable callable) throws Exception {
        // Keep track of direct memory used already, so we can check if we leek over and above what
        // we started with
        final long directMemoryUsedAtStart = getDirectMemoryUsedBytes();
        // run test in background thread
        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final var future = executorService.submit(callable);
        future.get(60, TimeUnit.MINUTES);
        executorService.shutdown();
        // check we did not leak direct memory now that the thread is shut down so thread locals
        // should be released.
        assertTrue(
                checkDirectMemoryIsCleanedUpToLessThanBaseUsage(directMemoryUsedAtStart),
                "Direct Memory used is more than base usage even after 20 gc() calls. At start was "
                        + (directMemoryUsedAtStart * Units.BYTES_TO_MEBIBYTES)
                        + "MB and is now "
                        + (getDirectMemoryUsedBytes() * Units.BYTES_TO_MEBIBYTES)
                        + "MB");
        // check db count
        assertEquals(
                0, VirtualDataSourceJasperDB.getCountOfOpenDatabases(), "Expected no open dbs");
    }

    /** Dump the Java heap to a file in current working directory */
    public static void dumpHeap() throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean =
                ManagementFactory.newPlatformMXBeanProxy(
                        server,
                        "com.sun.management:type=HotSpotDiagnostic",
                        HotSpotDiagnosticMXBean.class);
        final String filePath =
                Path.of("heapdump-" + System.currentTimeMillis() + ".hprof")
                        .toAbsolutePath()
                        .toString();
        mxBean.dumpHeap(filePath, true);
        System.out.println("Dumped heap to " + filePath);
    }

    /**
     * Check if direct memory used is less than base usage, calling gc() up to 20 times to try and
     * clean it up, checking each time. The limit of was chosen as big enough to not be effected by
     * any JVM internal use of direct memory and any cache maintained by sun.nio.ch.Util.
     *
     * <p><b>It is possible this is non-deterministic, because gc() is not guaranteed to free memory
     * and is async.</b>
     *
     * @param directMemoryBytesBefore The number of bytes of direct memory allocated before test was
     *     started
     * @return True if more than base usage of direct memory is being used after 20 gc() calls.
     */
    public static boolean checkDirectMemoryIsCleanedUpToLessThanBaseUsage(
            long directMemoryBytesBefore) {
        final long limit = directMemoryBytesBefore + DIRECT_MEMORY_BASE_USAGE;
        if (getDirectMemoryUsedBytes() < limit) {
            return true;
        }
        for (int i = 0; i < 20 && getDirectMemoryUsedBytes() > limit; i++) {
            System.gc();
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return getDirectMemoryUsedBytes() < limit;
    }

    private static final BufferPoolMXBean DIRECT_MEMORY_POOL;

    static {
        //noinspection OptionalGetWithoutIsPresent
        DIRECT_MEMORY_POOL =
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                        .filter(pool -> pool.getName().equals("direct"))
                        .findFirst()
                        .get();
    }

    /** Get the amount of direct memory used in bytes */
    public static long getDirectMemoryUsedBytes() {
        return DIRECT_MEMORY_POOL.getMemoryUsed();
    }

    /**
     * Creates a hash of the status of all files in a directory, that is their names, sizes and
     * modification dates. This is useful to be able to check if any modifications have happened on
     * a directory.
     *
     * @param dir The directory to scan and hash
     * @return null if directory doesn't exist or hash of status of contents
     */
    public static Hash hashDirectoryContentsStatus(final Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            final HashBuilder hashBuilder = new HashBuilder(DigestType.SHA_384);
            try (Stream<Path> filesStream = Files.walk(dir)) {
                filesStream.forEach(
                        filePath -> {
                            try {
                                hashBuilder.update(filePath.getFileName().toString().getBytes());
                                BasicFileAttributes fileAttributes =
                                        Files.readAttributes(filePath, BasicFileAttributes.class);
                                hashBuilder.update(fileAttributes.lastModifiedTime().toMillis());
                                hashBuilder.update(Files.size(filePath));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (Exception e) {
                System.err.println(
                        "Failed to hash directory [" + dir.toFile().getAbsolutePath() + "]");
                e.printStackTrace();
            }
            return hashBuilder.build();
        } else {
            return null;
        }
    }

    public static String toLongsString(final Hash hash) {
        final byte[] bytes = hash.getValue();
        final LongBuffer longBuf =
                ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asLongBuffer();
        final long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
    }

    public static String toLongsString(final ByteBuffer buf) {
        buf.rewind();
        final LongBuffer longBuf = buf.asLongBuffer();
        final long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        buf.rewind();
        return Arrays.toString(array);
    }

    /**
     * Creates a hash containing an int repeated 6 times as longs.
     *
     * @return hash with digest an array of 6 longs determined by the given value
     */
    public static Hash hash(final int value) {
        final byte[] hardCoded =
                new byte[] {
                    (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
                };
        final Hash mutableHash = new Hash(DigestType.SHA_384);
        final byte[] digest = mutableHash.getValue();
        for (int i = 0; i < 6; i++) {
            System.arraycopy(hardCoded, 0, digest, i * 6 + 4, 4);
        }
        return mutableHash;
    }

    public static void hexDump(final PrintStream out, final Path file) throws IOException {
        InputStream is = new FileInputStream(file.toFile());
        int i = 0;

        while (is.available() > 0) {
            final StringBuilder sb1 = new StringBuilder();
            final StringBuilder sb2 = new StringBuilder("   ");
            out.printf("%04X  ", i * 16);
            for (int j = 0; j < 16; j++) {
                if (is.available() > 0) {
                    int value = is.read();
                    sb1.append(String.format("%02X ", value));
                    if (!Character.isISOControl(value)) {
                        sb2.append((char) value);
                    } else {
                        sb2.append(".");
                    }
                } else {
                    for (; j < 16; j++) {
                        sb1.append("   ");
                    }
                }
            }
            out.print(sb1);
            out.println(sb2);
            i++;
        }
        is.close();
    }

    /** Code from method java.util.Collections.shuffle(); */
    public static int[] shuffle(Random random, final int[] array) {
        if (random == null) {
            random = new Random();
        }
        final int count = array.length;
        for (int i = count; i > 1; i--) {
            swap(array, i - 1, random.nextInt(i));
        }
        return array;
    }

    private static void swap(final int[] array, final int i, final int j) {
        final int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    public static String randomString(final int length, final Random random) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public static byte[] randomUtf8Bytes(final int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    /** Do a standard "ls -lh" on a directory or file and print results to System.out. */
    public static void ls(Path dir) {
        System.out.println(
                "=== " + dir + " ======================================================");
        try {
            ProcessBuilder pb = new ProcessBuilder("ls", "-Rlh", dir.toAbsolutePath().toString());
            pb.inheritIO();
            pb.start().waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("===================================================================");
    }
}

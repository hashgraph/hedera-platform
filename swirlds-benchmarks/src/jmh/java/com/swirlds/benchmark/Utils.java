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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Utils {

    private static final Logger LOG = LogManager.getLogger(Utils.class);

    private Utils() {
        // do not instantiate
    }

    public static void deleteRecursively(Path path) {
        if (Files.notExists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ex) {
                                    LOG.warn("Couldn't delete {}: {}", p, ex.getMessage());
                                }
                            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void printClassHistogram(int topN) {
        try {
            ObjectName objectName = new ObjectName("com.sun.management:type=DiagnosticCommand");
            String operationName = "gcClassHistogram";
            Object[] params = {null};
            String[] signature = {String[].class.getName()};

            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            String result =
                    (String) mbeanServer.invoke(objectName, operationName, params, signature);

            StringBuilder sb = new StringBuilder("Class Histogram:\n");
            if (topN > 0) {
                String[] lines = result.split("\n");
                Arrays.stream(lines).limit(topN).forEach(line -> sb.append(line).append("\n"));
                sb.append(" ...\n").append(lines[lines.length - 1]);
            } else {
                sb.append(result);
            }
            LOG.info(sb);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /* Random utils */

    public static long randomLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static long randomLong(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }

    public static int randomInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    public static byte[] toBytes(long seed, int size) {
        byte[] bytes = new byte[size];
        long val = seed;
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) val;
            val = (val >>> 8) | (val << 56);
        }
        return bytes;
    }

    public static long fromBytes(byte[] bytes) {
        long val = 0;
        for (int i = 0; i < Math.min(bytes.length, 8); ++i) {
            val |= ((long) bytes[i] & 0xff) << (i * 8);
        }
        return val;
    }
}

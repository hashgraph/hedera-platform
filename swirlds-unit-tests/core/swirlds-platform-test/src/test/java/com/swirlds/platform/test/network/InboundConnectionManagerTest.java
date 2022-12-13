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
package com.swirlds.platform.test.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.Connection;
import com.swirlds.platform.network.InboundConnectionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InboundConnectionManagerTest {
    @Test
    void test() throws InterruptedException {
        final int numThreads = 10;
        final int numConnections = 10;

        final InboundConnectionManager lcs = new InboundConnectionManager();
        final Queue<FakeConnection> all = new ConcurrentLinkedQueue<>();
        final CountDownLatch allDone = new CountDownLatch(numThreads * numConnections);
        final List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    for (int c = 0; c < numConnections; c++) {
                                        final FakeConnection fc = new FakeConnection();
                                        lcs.newConnection(fc);
                                        all.add(fc);
                                        allDone.countDown();
                                        final Random r = new Random();
                                        if (r.nextBoolean()) {
                                            // sleep sometimes, a variable amount of time
                                            Thread.sleep(r.nextInt(10) + 1);
                                        }
                                    }
                                } catch (final InterruptedException e) {
                                    e.printStackTrace();
                                }
                            });
            threads.add(thread);
            thread.start();
        }

        while (allDone.getCount() > 0) {
            System.out.println("Connections to go " + allDone.getCount());
            final Connection c = lcs.waitForConnection();
            if (c instanceof FakeConnection fc) {
                // wait until either all threads are done, or the current connection is disconnected
                boolean done = false;
                while (!done) {
                    done = fc.awaitDisconnect() || allDone.await(1, TimeUnit.MILLISECONDS);
                }
            } else {
                throw new RuntimeException("all connections should be FakeConnection");
            }
        }
        for (final Thread thread : threads) {
            thread.join();
        }

        final Connection lastConn = lcs.waitForConnection();
        assertSame(
                lastConn,
                lcs.getConnection(),
                "both methods should return the same connection "
                        + "since there are no new connections being established");
        assertTrue(lastConn.connected(), "the last remaining connection should be connected");
        assertEquals(
                numThreads * numConnections,
                all.size(),
                "all threads should have created the set number of connections");
        for (final FakeConnection fc : all) {
            if (fc == lastConn) {
                assertTrue(fc.connected(), "last connection should be connected");
            } else {
                assertFalse(
                        fc.connected(),
                        "all connections except the last one should be disconnected");
            }
        }
    }
}

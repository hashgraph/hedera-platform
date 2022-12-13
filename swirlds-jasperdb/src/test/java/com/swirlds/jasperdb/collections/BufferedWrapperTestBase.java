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

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BufferedWrapperTestBase {

    protected static void waitABit() {
        try {
            MICROSECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted!", e);
        }
    }

    protected static final class CoordinatedThread extends Thread {
        public CoordinatedThread(
                final CountDownLatch startGate, final CountDownLatch endGate, final Runnable r) {
            super(
                    () -> {
                        try {
                            if (!startGate.await(1, TimeUnit.SECONDS)) {
                                throw new RuntimeException("Timed out waiting for test to start!");
                            }

                            r.run();

                            endGate.countDown();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }
}

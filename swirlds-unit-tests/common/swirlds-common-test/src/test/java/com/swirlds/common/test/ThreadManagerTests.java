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
package com.swirlds.common.test;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.threading.manager.StandardThreadManager;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.LifecycleException;
import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadManager Tests")
class ThreadManagerTests {

    @Test
    @DisplayName("Ad Hoc Manager Always Allows Threads Test")
    void adHocManagerAlwaysAllowsThreadsTest() throws InterruptedException {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final Thread thread = getStaticThreadManager().createThread(null, () -> executed.set(true));
        thread.start();
        assertEventuallyTrue(executed::get, Duration.ofSeconds(1), "thread should have run by now");
        thread.join(1_000);
        assertFalse(thread.isAlive(), "thread should have terminated");
    }

    @Test
    @DisplayName("Ad Hoc Manager Always Allows Factory Threads Test")
    void adHocManagerAlwaysAllowsFactoryThreadsTest() throws InterruptedException {
        final ThreadFactory factory = getStaticThreadManager().createThreadFactory("test", "test");

        final AtomicBoolean executed1 = new AtomicBoolean(false);
        final Thread thread1 = factory.newThread(() -> executed1.set(true));
        thread1.start();
        assertEventuallyTrue(
                executed1::get, Duration.ofSeconds(1), "thread should have run by now");
        thread1.join(1_000);
        assertFalse(thread1.isAlive(), "thread should have terminated");

        final AtomicBoolean executed2 = new AtomicBoolean(false);
        final Thread thread2 = factory.newThread(() -> executed2.set(true));
        thread2.start();
        assertEventuallyTrue(
                executed2::get, Duration.ofSeconds(1), "thread should have run by now");
        thread2.join(1_000);
        assertFalse(thread2.isAlive(), "thread should have terminated");
    }

    @Test
    @DisplayName("Standard Manager Only Allows Threads After Start Test")
    void standardManagerOnlyAllowsThreadsAfterStartTest() throws InterruptedException {
        final ThreadManager manager = new StandardThreadManager();
        assertThrows(
                LifecycleException.class,
                () -> manager.createThread(null, () -> {}),
                "manager is not started yet");

        manager.start();

        final AtomicBoolean executed = new AtomicBoolean(false);
        final Thread thread = manager.createThread(null, () -> executed.set(true));
        thread.start();
        assertEventuallyTrue(executed::get, Duration.ofSeconds(1), "thread should have run by now");
        thread.join(1_000);
        assertFalse(thread.isAlive(), "thread should have terminated");

        manager.stop();
        assertThrows(
                LifecycleException.class,
                () -> manager.createThread(null, () -> {}),
                "manager was stopped");
    }

    @Test
    @DisplayName("Standard Manager Only Allows Factory Threads After Start Test")
    void standardManagerOnlyAllowsFactoryThreadsAfterStartTest() throws InterruptedException {
        final ThreadManager manager = new StandardThreadManager();
        final ThreadFactory factory = manager.createThreadFactory("test", "test");
        assertThrows(
                LifecycleException.class,
                () -> factory.newThread(() -> {}),
                "manager is not started yet");

        manager.start();

        final AtomicBoolean executed1 = new AtomicBoolean(false);
        final Thread thread1 = factory.newThread(() -> executed1.set(true));
        thread1.start();
        assertEventuallyTrue(
                executed1::get, Duration.ofSeconds(1), "thread should have run by now");
        thread1.join(1_000);
        assertFalse(thread1.isAlive(), "thread should have terminated");

        // Create second thread to make sure factory can create multiple threads
        final AtomicBoolean executed2 = new AtomicBoolean(false);
        final Thread thread2 = factory.newThread(() -> executed2.set(true));
        thread2.start();
        assertEventuallyTrue(
                executed2::get, Duration.ofSeconds(1), "thread should have run by now");
        thread2.join(1_000);
        assertFalse(thread2.isAlive(), "thread should have terminated");

        manager.stop();
        assertThrows(
                LifecycleException.class,
                () -> factory.newThread(() -> {}),
                "manager is not started yet");
    }
}

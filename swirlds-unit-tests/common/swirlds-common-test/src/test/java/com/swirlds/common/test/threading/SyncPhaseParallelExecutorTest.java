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
package com.swirlds.common.test.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class SyncPhaseParallelExecutorTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Test phases in order")
    @Disabled("ticket opened #5316")
    void testPhasesInOrder() throws Throwable {
        final AtomicReference<SyncPhaseParallelExecutor> phaseExecutorRef =
                new AtomicReference<>(null);
        final ThreadTask threadTask1 = new ThreadTask(phaseExecutorRef::get);
        final ThreadTask threadTask2 = new ThreadTask(phaseExecutorRef::get);
        final AtomicInteger afterPhaseChecksRan = new AtomicInteger(0);

        // assertions to run after phases
        final Runnable afterPhase1 =
                () -> {
                    assertEquals(
                            2,
                            threadTask1.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(
                            2,
                            threadTask2.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(1, phaseExecutorRef.get().getPhase(), "phase should be 1");
                    afterPhaseChecksRan.incrementAndGet();
                };
        final Runnable afterPhase2 =
                () -> {
                    assertEquals(
                            4,
                            threadTask1.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(
                            4,
                            threadTask2.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(2, phaseExecutorRef.get().getPhase(), "phase should be 2");
                    afterPhaseChecksRan.incrementAndGet();
                };

        // set the ref to a new executor
        phaseExecutorRef.set(new SyncPhaseParallelExecutor(afterPhase1, afterPhase2));

        // setup the background thread
        final Thread thread = new Thread(threadTask1.getThreadRun());
        final AtomicReference<Throwable> backgroundThreadThrowable = new AtomicReference<>(null);
        thread.setUncaughtExceptionHandler((t, e) -> backgroundThreadThrowable.set(e));

        // start the background thread
        thread.start();
        // run the second task in the foreground
        threadTask2.getThreadRun().run();
        // wait for the background thread to be done
        thread.join();
        if (backgroundThreadThrowable.get() != null) {
            throw backgroundThreadThrowable.get();
        }

        assertEquals(
                1,
                phaseExecutorRef.get().getPhase(),
                "after all phases run, it should go back to 1");
        assertEquals(2, afterPhaseChecksRan.get(), "lambdas should have run after phases 1 & 2");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Test single thread option")
    void testSingleThreadOption() {
        final AtomicReference<SyncPhaseParallelExecutor> phaseExecutorRef =
                new AtomicReference<>(null);
        final ThreadTask threadTask = new ThreadTask(phaseExecutorRef::get);
        final AtomicInteger afterPhaseChecksRan = new AtomicInteger(0);

        // assertions to run after phases
        final Runnable afterPhase1 =
                () -> {
                    assertEquals(
                            2,
                            threadTask.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(1, phaseExecutorRef.get().getPhase(), "phase should be 1");
                    afterPhaseChecksRan.incrementAndGet();
                };
        final Runnable afterPhase2 =
                () -> {
                    assertEquals(
                            4,
                            threadTask.getNumberCompleted(),
                            "should complete 2 tasks per phase");
                    assertEquals(2, phaseExecutorRef.get().getPhase(), "phase should be 2");
                    afterPhaseChecksRan.incrementAndGet();
                };

        // set the ref to a new executor
        phaseExecutorRef.set(new SyncPhaseParallelExecutor(afterPhase1, afterPhase2, false));

        // run the task in the foreground
        threadTask.getThreadRun().run();

        assertEquals(
                1,
                phaseExecutorRef.get().getPhase(),
                "after all phases run, it should go back to 1");
        assertEquals(2, afterPhaseChecksRan.get(), "lambdas should have run after phases 1 & 2");
    }

    private static class ThreadTask {
        private final AtomicInteger numberCompleted;
        private final Callable<Void> doTask;
        private final Runnable threadRun;

        public ThreadTask(final Supplier<SyncPhaseParallelExecutor> phaseExecutorRef) {
            numberCompleted = new AtomicInteger(0);
            doTask =
                    () -> {
                        numberCompleted.incrementAndGet();
                        return null;
                    };
            threadRun =
                    () -> {
                        try {
                            // execute 3 phases
                            phaseExecutorRef.get().doParallel(doTask, doTask);
                            phaseExecutorRef.get().doParallel(doTask, doTask);
                            phaseExecutorRef.get().doParallel(doTask, doTask);
                        } catch (ParallelExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    };
        }

        public int getNumberCompleted() {
            return numberCompleted.get();
        }

        public Runnable getThreadRun() {
            return threadRun;
        }
    }
}

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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.interrupt.InterruptableSupplier;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.common.utility.ValueReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Contains various useful assertions. */
public final class AssertionUtils {

    private AssertionUtils() {}

    /**
     * Assert that a condition eventually becomes true. Sleeps for 1ms between each check of the
     * condition.
     *
     * <p>This method is NOT safe to interrupt. Interrupting this method may case an assertion to be
     * triggered that fails the test.
     *
     * @param condition the condition to test
     * @param maxDuration the maximum amount of time to wait
     * @param message a message that is displayed if the condition does not become true
     * @param cause provides the thing that caused this assertion to fail
     */
    public static void assertEventuallyTrue(
            final BooleanSupplier condition,
            final Duration maxDuration,
            final String message,
            final Supplier<Throwable> cause) {

        final Instant start = Instant.now();

        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }

            final Instant now = Instant.now();
            final Duration elapsed = Duration.between(start, now);
            if (CompareTo.isGreaterThan(elapsed, maxDuration)) {
                condition.getAsBoolean();
                if (cause == null) {
                    fail(message);
                } else {
                    fail(message, cause.get());
                }
            }

            try {
                MILLISECONDS.sleep(1);
            } catch (final InterruptedException e) {
                fail("test was interrupted", e);
            }
        }
    }

    /**
     * Assert that a condition eventually becomes true. Sleeps for 1ms between each check of the
     * condition.
     *
     * <p>This method is NOT safe to interrupt. Interrupting this method may case an assertion to be
     * triggered that fails the test.
     *
     * @param condition the condition to test
     * @param maxDuration the maximum amount of time to wait
     * @param message a message that is displayed if the condition does not become true
     */
    public static void assertEventuallyTrue(
            final BooleanSupplier condition, final Duration maxDuration, final String message) {
        assertEventuallyTrue(condition, maxDuration, message, null);
    }

    /**
     * Assert that a condition eventually becomes false. Sleeps for 1ms between each check of the
     * condition.
     *
     * <p>This method is NOT safe to interrupt. Interrupting this method may case an assertion to be
     * triggered that fails the test.
     *
     * @param condition the condition to test
     * @param maxDuration the maximum amount of time to wait
     * @param message a message that is displayed if the condition does not become false
     */
    public static void assertEventuallyFalse(
            final BooleanSupplier condition, final Duration maxDuration, final String message) {
        assertEventuallyTrue(() -> !condition.getAsBoolean(), maxDuration, message);
    }

    /**
     * Assert that a method eventually returns an object equal to the expected value.
     *
     * <p>A common reason for this method to fail is if it attempts to compare two similar but
     * different data types, for example an integer and a long. Ensure that both data types are
     * exactly the same.
     *
     * @param expected the expected value
     * @param actual a method that returns a value
     * @param maxDuration the max amount of time to wait
     * @param message a message that is displayed if the expected object is never returned
     * @param <T> the type of the object being compared
     */
    public static <T> void assertEventuallyEquals(
            final T expected,
            final Supplier<T> actual,
            final Duration maxDuration,
            final String message) {
        assertEventuallyTrue(() -> Objects.equals(expected, actual.get()), maxDuration, message);
    }

    /**
     * Assert that a method eventually returns an object that is the same as the expected value.
     *
     * @param expected the expected value
     * @param actual a method that returns a value
     * @param maxDuration the max amount of time to wait
     * @param message a message that is displayed if the expected object is never returned
     */
    public static void assertEventuallySame(
            final Object expected,
            final Supplier<Object> actual,
            final Duration maxDuration,
            final String message) {
        assertEventuallyTrue(() -> expected == actual.get(), maxDuration, message);
    }

    /**
     * Assert that an operation eventually stops throwing exceptions.
     *
     * @param operation the operation to test
     * @param maxDuration the maximum amount of time to wait
     * @param message a message that is displayed if the method does not stop throwing
     */
    public static void assertEventuallyDoesNotThrow(
            final Runnable operation, final Duration maxDuration, final String message) {

        final ValueReference<Throwable> mostRecentException = new ValueReference<>();

        assertEventuallyTrue(
                () -> {
                    try {
                        operation.run();
                        return true;
                    } catch (final Throwable e) {
                        mostRecentException.setValue(e);
                        return false;
                    }
                },
                maxDuration,
                message,
                mostRecentException::getValue);
    }

    /**
     * Run an operation and throw an exception if it takes too long.
     *
     * @param operation the operation to run
     * @param maxDuration the maximum amount of time to wait for the operation to complete
     * @param message an error message if the operation takes too long
     */
    public static void completeBeforeTimeout(
            final InterruptableRunnable operation, final Duration maxDuration, final String message)
            throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean error = new AtomicBoolean();

        new ThreadConfiguration()
                .setComponent("assertion-utils")
                .setThreadName("assert-prompt-completion")
                .setInterruptableRunnable(
                        () -> {
                            operation.run();
                            latch.countDown();
                        })
                .setExceptionHandler(
                        (final Thread thread, final Throwable exception) -> {
                            error.set(true);
                            exception.printStackTrace();
                        })
                .build(true);

        assertFalse(error.get(), "exception encountered while handling operation");
        final boolean completed = latch.await(maxDuration.toMillis(), MILLISECONDS);
        assertTrue(completed, message);
    }

    /**
     * Run an operation and throw an exception if it takes too long.
     *
     * @param operation the operation to run
     * @param maxDuration the maximum amount of time to wait for the operation to complete
     * @param message an error message if the operation takes too long
     * @return the value returned by the operation
     */
    public static <T> T completeBeforeTimeout(
            final InterruptableSupplier<T> operation,
            final Duration maxDuration,
            final String message)
            throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicReference<T> value = new AtomicReference<>();

        new ThreadConfiguration()
                .setComponent("assertion-utils")
                .setThreadName("assert-prompt-completion")
                .setInterruptableRunnable(
                        () -> {
                            value.set(operation.get());
                            latch.countDown();
                        })
                .setExceptionHandler(
                        (final Thread thread, final Throwable exception) -> {
                            error.set(true);
                            exception.printStackTrace();
                        })
                .build(true);

        assertFalse(error.get(), "exception encountered while handling operation");
        final boolean completed = latch.await(maxDuration.toMillis(), MILLISECONDS);
        assertTrue(completed, message);

        return value.get();
    }

    /**
     * Run an operation and fail if the operation takes too long to throw an exception or if the
     * exception type is wrong.
     *
     * @param expectedException the exception that is expected
     * @param operation the operation to run
     * @param maxDuration the maximum amount of time to wait for the operation to complete
     * @param message an error message if the operation takes too long or if the wrong type is
     *     thrown
     */
    public static void throwBeforeTimeout(
            final Class<? extends Throwable> expectedException,
            final Runnable operation,
            final Duration maxDuration,
            final String message)
            throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean error = new AtomicBoolean();

        new ThreadConfiguration()
                .setComponent("assertion-utils")
                .setThreadName("assert-prompt-throw")
                .setRunnable(
                        () -> {
                            assertThrows(expectedException, operation::run, message);
                            latch.countDown();
                        })
                .setExceptionHandler(
                        (final Thread thread, final Throwable exception) -> {
                            error.set(true);
                            exception.printStackTrace();
                        })
                .build(true);

        assertFalse(error.get(), message);

        final boolean completed = latch.await(maxDuration.toMillis(), MILLISECONDS);
        assertTrue(completed, message);
    }
}

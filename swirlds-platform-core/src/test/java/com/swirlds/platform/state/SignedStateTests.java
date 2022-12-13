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
package com.swirlds.platform.state;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignedState Tests")
class SignedStateTests {

    /** Generate a signed state. FUTURE WORK: replace this with the utility added to 0.29.0 */
    private SignedState generateSignedState(final Random random, final State state) {
        return new SignedState(
                state,
                random.nextLong(),
                random.nextLong(),
                RandomUtils.randomHash(random),
                new RandomAddressBookGenerator(random).build(),
                new EventImpl[0],
                RandomUtils.randomInstant(random),
                random.nextBoolean(),
                new LinkedList<>(),
                new BasicSoftwareVersion(random.nextInt()));
    }

    /**
     * Build a mock state.
     *
     * @param reserveCallback this method is called when the State is reserved
     * @param archiveCallback this method is called when the SwirldState is archived
     * @param releaseCallback this method is called when the State is released
     */
    private State buildMockState(
            final Runnable reserveCallback,
            final Runnable archiveCallback,
            final Runnable releaseCallback) {

        final State state = mock(State.class);
        final SwirldState swirldState = mock(SwirldState2.class);

        when(state.getSwirldState()).thenReturn(swirldState);

        if (reserveCallback != null) {
            doAnswer(
                            invocation -> {
                                reserveCallback.run();
                                return null;
                            })
                    .when(state)
                    .reserve();
        }

        if (archiveCallback != null) {
            doAnswer(
                            invocation -> {
                                archiveCallback.run();
                                return null;
                            })
                    .when(swirldState)
                    .archive();
        }

        if (releaseCallback != null) {
            doAnswer(
                            invocation -> {
                                releaseCallback.run();
                                return null;
                            })
                    .when(state)
                    .release();
        }

        return state;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Strong Reservation Test")
    void strongReservationTest(final boolean explicit) throws InterruptedException {
        final Random random = new Random();
        final SignedStateGarbageCollector signedStateGarbageCollector =
                new SignedStateGarbageCollector(null);
        signedStateGarbageCollector.start();

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean archived = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final Thread mainThread = Thread.currentThread();

        final State state =
                buildMockState(
                        () -> {
                            assertFalse(reserved.get(), "should only be reserved once");
                            reserved.set(true);
                        },
                        () -> {
                            archived.set(true);
                            fail("state should not be archived during this test");
                        },
                        () -> {
                            assertFalse(released.get(), "should only be released once");
                            assertNotSame(
                                    mainThread,
                                    Thread.currentThread(),
                                    "release should happen on background thread");
                            released.set(true);
                        });

        final SignedState signedState = generateSignedState(random, state);
        signedState.setGarbageCollector(signedStateGarbageCollector);

        if (explicit) {
            signedState.reserveState();
        }

        // Nothing should happen during this sleep, but give the background thread time to misbehave
        // if it wants to
        MILLISECONDS.sleep(10);

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        // Taking and releasing weak reservations should have no impact when a strong (implicit)
        // reservation is held
        for (int i = 0; i < 10; i++) {
            signedState.weakReserveState();
        }
        for (int i = 0; i < 10; i++) {
            signedState.weakReleaseState();
        }

        if (explicit) {
            // Taking strong reservations should have no impact as long as we don't delete all of
            // them
            for (int i = 0; i < 10; i++) {
                signedState.reserveState();
            }
            for (int i = 0; i < 10; i++) {
                signedState.releaseState();
            }
        }

        // Nothing should happen during this sleep, but give the background thread time to misbehave
        // if it wants to
        MILLISECONDS.sleep(10);

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        signedState.releaseState();

        assertThrows(
                ReferenceCountException.class,
                signedState::reserveState,
                "should not be able to reserve after full release");
        assertThrows(
                ReferenceCountException.class,
                signedState::weakReserveState,
                "should not be able to weak reserve after full release");
        assertThrows(
                ReferenceCountException.class,
                signedState::releaseState,
                "should not be able to release again");
        assertThrows(
                ReferenceCountException.class,
                signedState::weakReleaseState,
                "should not be able to weak release again");

        assertEventuallyTrue(
                released::get, Duration.ofSeconds(1), "state should eventually be released");
        assertFalse(archived.get(), "state should not be archived");

        signedStateGarbageCollector.stop();
    }

    @DisplayName("Weak Reservation Test")
    @Test
    void weakReservationTest() throws InterruptedException {
        final Random random = new Random();
        final SignedStateGarbageCollector signedStateGarbageCollector =
                new SignedStateGarbageCollector(null);
        signedStateGarbageCollector.start();

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean archived = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final Thread mainThread = Thread.currentThread();

        final State state =
                buildMockState(
                        () -> {
                            assertFalse(reserved.get(), "should only be reserved once");
                            reserved.set(true);
                        },
                        () -> {
                            assertFalse(archived.get(), "should only be archived once");
                            assertNotSame(
                                    mainThread,
                                    Thread.currentThread(),
                                    "release should happen on background thread");
                            archived.set(true);
                        },
                        () -> {
                            assertFalse(released.get(), "should only be released once");
                            assertNotSame(
                                    mainThread,
                                    Thread.currentThread(),
                                    "release should happen on background thread");
                            released.set(true);
                        });

        final SignedState signedState = generateSignedState(random, state);
        signedState.setGarbageCollector(signedStateGarbageCollector);
        assertTrue(reserved.get(), "State should have been reserved");

        // while holding a weak reservation, the state will be permitted to be archived but not
        // deleted
        signedState.weakReserveState();

        signedState.releaseState();

        assertEventuallyTrue(
                archived::get, Duration.ofSeconds(1), "state should have been archived by now");
        assertFalse(released.get(), "should not be released yet");

        // Nothing should happen during this sleep, but give the background thread time to misbehave
        // if it wants to
        MILLISECONDS.sleep(10);

        assertFalse(released.get(), "should not be released yet");

        signedState.weakReserveState();
        signedState.weakReserveState();
        signedState.weakReleaseState();
        signedState.weakReleaseState();

        assertFalse(released.get(), "should not be released yet");

        // We are still holding a weak reservation
        // Nothing should happen during this sleep, but give the background thread time to misbehave
        // if it wants to
        MILLISECONDS.sleep(10);

        assertFalse(released.get(), "should not be released yet");

        signedState.weakReleaseState();

        assertEventuallyTrue(
                released::get, Duration.ofSeconds(1), "state should have been released by now");
        signedStateGarbageCollector.stop();
    }

    @Test
    @DisplayName("Finite Deletion Queue Test")
    void finiteDeletionQueueTest() throws InterruptedException {
        final Random random = new Random();
        final SignedStateGarbageCollector signedStateGarbageCollector =
                new SignedStateGarbageCollector(null);
        signedStateGarbageCollector.start();

        // Deletion thread will hold one after it is removed from the queue, hence the +1
        final int capacity = SignedStateGarbageCollector.DELETION_QUEUE_CAPACITY + 1;

        final AtomicInteger deletionCount = new AtomicInteger();
        final CountDownLatch deletionBlocker = new CountDownLatch(1);

        final Thread mainThread = Thread.currentThread();

        for (int i = 0; i < capacity; i++) {
            final State state =
                    buildMockState(
                            null,
                            null,
                            () -> {
                                abortAndThrowIfInterrupted(
                                        deletionBlocker::await, "unexpected interruption");
                                deletionCount.getAndIncrement();
                            });

            final SignedState signedState = generateSignedState(random, state);
            signedState.setGarbageCollector(signedStateGarbageCollector);
            signedState.releaseState();
        }

        // At this point in time, the signed state deletion queue should be entirely filled up.
        // Deleting one more signed state should cause the deletion to happen on the current thread.

        final State state =
                buildMockState(
                        null,
                        null,
                        () -> {
                            assertSame(
                                    mainThread, Thread.currentThread(), "called on wrong thread");
                            deletionCount.getAndIncrement();
                        });

        final SignedState signedState = generateSignedState(random, state);
        signedState.releaseState();

        assertEquals(1, deletionCount.get());

        // Nothing should happen during this sleep, but give the background thread time to misbehave
        // if it wants to
        MILLISECONDS.sleep(10);

        assertEquals(1, deletionCount.get());

        deletionBlocker.countDown();
        assertEventuallyEquals(
                capacity + 1,
                deletionCount::get,
                Duration.ofSeconds(1),
                "all states should eventually be deleted");
        signedStateGarbageCollector.stop();
    }

    /**
     * Although this lifecycle is not expected in a real system, it's a nice for the sake of
     * completeness to ensure that a signed state can clean itself up without having an associated
     * garbage collection thread.
     */
    @Test
    @DisplayName("No Garbage Collector Test")
    void noGarbageCollectorTest() {
        final Random random = new Random();

        final AtomicBoolean reserved = new AtomicBoolean(false);
        final AtomicBoolean archived = new AtomicBoolean(false);
        final AtomicBoolean released = new AtomicBoolean(false);

        final Thread mainThread = Thread.currentThread();

        final State state =
                buildMockState(
                        () -> {
                            assertFalse(reserved.get(), "should only be reserved once");
                            reserved.set(true);
                        },
                        () -> {
                            archived.set(true);
                            fail("state should not be archived during this test");
                        },
                        () -> {
                            assertFalse(released.get(), "should only be released once");
                            assertSame(
                                    mainThread,
                                    Thread.currentThread(),
                                    "release should happen on main thread");
                            released.set(true);
                        });

        final SignedState signedState = generateSignedState(random, state);

        signedState.reserveState();

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        // Taking and releasing weak reservations should have no impact when a strong (implicit)
        // reservation is held
        for (int i = 0; i < 10; i++) {
            signedState.weakReserveState();
        }
        for (int i = 0; i < 10; i++) {
            signedState.weakReleaseState();
        }

        // Taking strong reservations should have no impact as long as we don't delete all of them
        for (int i = 0; i < 10; i++) {
            signedState.reserveState();
        }
        for (int i = 0; i < 10; i++) {
            signedState.releaseState();
        }

        assertTrue(reserved.get(), "State should have been reserved");
        assertFalse(archived.get(), "state should not be archived");
        assertFalse(released.get(), "state should not be deleted");

        signedState.releaseState();

        assertThrows(
                ReferenceCountException.class,
                signedState::reserveState,
                "should not be able to reserve after full release");
        assertThrows(
                ReferenceCountException.class,
                signedState::weakReserveState,
                "should not be able to weak reserve after full release");
        assertThrows(
                ReferenceCountException.class,
                signedState::releaseState,
                "should not be able to release again");
        assertThrows(
                ReferenceCountException.class,
                signedState::weakReleaseState,
                "should not be able to weak release again");

        assertEventuallyTrue(
                released::get, Duration.ofSeconds(1), "state should eventually be released");
        assertFalse(archived.get(), "state should not be archived");
    }

    /** There used to be a bug (now fixed) that would case this test to fail. */
    @Test
    @DisplayName("Alternate Constructor Reservations Test")
    void alternateConstructorReservationsTest() {
        final State state = new State();
        final SignedState signedState = new SignedState(state);

        assertFalse(state.isDestroyed(), "state should not yet be destroyed");

        // Taking and then releasing a weak reservation should not have any effect while a strong
        // reservation is (implicitly) held.
        signedState.weakReserveState();
        signedState.weakReleaseState();
        assertFalse(state.isDestroyed(), "state should not yet be destroyed");

        signedState.reserveState();

        // Taking and then releasing a weak reservation should not have any effect while a strong
        // reservation is (explicitly) held.
        signedState.weakReserveState();
        signedState.weakReleaseState();
        assertFalse(state.isDestroyed(), "state should not yet be destroyed");

        signedState.releaseState();

        assertTrue(state.isDestroyed(), "state should now be destroyed");
    }
}

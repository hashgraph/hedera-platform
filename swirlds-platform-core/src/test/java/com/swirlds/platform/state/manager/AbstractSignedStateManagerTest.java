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
package com.swirlds.platform.state.manager;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Settings;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.invocation.InvocationOnMock;

/** Boilerplate implementation for SignedStateManager tests. */
class AbstractSignedStateManagerTest {

    protected final Random random = getRandomPrintSeed();

    protected final AtomicInteger stateTrackedCount = new AtomicInteger();
    protected AtomicInteger selfSignedCount = new AtomicInteger();
    protected AtomicInteger stateLacksSignaturesCount = new AtomicInteger();
    protected AtomicInteger stateHasEnoughSignaturesCount = new AtomicInteger();

    protected final Map<Long /* round */, SignedState> signedStates = new ConcurrentHashMap<>();
    protected final AtomicLong highestRound = new AtomicLong(-1);
    protected final int roundsNonAncient = 5;

    /** true if an error occurs on a notification thread */
    protected final AtomicBoolean error = new AtomicBoolean(false);

    @BeforeEach
    protected void beforeEach() {
        NotificationFactory.getEngine().unregisterAll();
        Settings.getInstance().getState().roundsNonAncient = roundsNonAncient;
        Settings.getInstance().getState().maxAgeOfFutureStateSignatures = roundsNonAncient;
    }

    @AfterEach
    protected void afterEach() {
        NotificationFactory.getEngine().unregisterAll();
        assertFalse(error.get(), "error detected");
    }

    /** Syntactic sugar for registering a listener with exception handling. */
    @SuppressWarnings("unchecked")
    protected <N extends Notification, L extends Listener<N>> void registerListener(
            final Class<L> listenerClass, final Consumer<N> callback) {

        // This funky inline class is necessary to make the compiler happy with the generics
        class InlineListener implements Listener<N> {
            @Override
            public void notify(final N data) {
                try {
                    callback.accept(data);
                } catch (final Throwable ex) {
                    ex.printStackTrace();
                    error.set(true);
                }
            }
        }

        assertTrue(
                NotificationFactory.getEngine().register(listenerClass, (L) new InlineListener()),
                "problem registering listener");
    }

    /** Add a signature for a node on a state from a given round. */
    protected void addSignature(
            final SignedStateManager manager, final long round, final long nodeId) {

        final SignedState signedState = signedStates.get(round);

        if (signedState == null) {
            // We are being asked to sign a non-existent round.
            return;
        }

        final AddressBook addressBook = signedState.getAddressBook();
        final Hash hash = signedState.getState().getHash();

        // Although we normally want to avoid rebuilding the dispatcher over and over, the slight
        // performance overhead is worth the convenience during unit tests
        manager.preConsensusSignatureObserver(
                round,
                nodeId,
                hash,
                buildFakeSignature(addressBook.getAddress(nodeId).getSigPublicKey(), hash));
    }

    /**
     * Validate that callbacks were correctly invoked. Will wait up to 1 second for callbacks to
     * properly be invoked.
     */
    protected void validateCallbackCounts(
            final int expectedStateTrackedCount,
            final int expectedSelfSignedCount,
            final int expectedStateLacksSignaturesCount,
            final int expectedStateHasEnoughSignaturesCount) {

        assertEventuallyDoesNotThrow(
                () -> {
                    assertEquals(
                            expectedStateTrackedCount,
                            stateTrackedCount.get(),
                            "unexpected number of callbacks");
                    assertEquals(
                            expectedSelfSignedCount,
                            selfSignedCount.get(),
                            "unexpected number of callbacks");
                    assertEquals(
                            expectedStateLacksSignaturesCount,
                            stateLacksSignaturesCount.get(),
                            "unexpected number of callbacks");
                    assertEquals(
                            expectedStateHasEnoughSignaturesCount,
                            stateHasEnoughSignaturesCount.get(),
                            "unexpected number of callbacks");
                },
                Duration.ofSeconds(1),
                "callbacks not correctly invoked");
    }

    protected void validateReservationCounts(final Predicate<Long> shouldRoundBePresent) {

        // Check reservation counts. Only the 5 most recent states should have reservations.
        for (final SignedState signedState : signedStates.values()) {
            final long round = signedState.getRound();
            if (shouldRoundBePresent.test(round)) {
                assertEquals(
                        -1, signedState.getReservations(), "state should have no reservations");
                assertEquals(
                        -1,
                        signedState.getWeakReservations(),
                        "state should have no weak reservations");
            } else {
                if (round == highestRound.get()) {
                    // the most recent state has an extra reservation
                    assertEquals(2, signedState.getReservations(), "unexpected reservation count");
                } else {
                    assertEquals(1, signedState.getReservations(), "unexpected reservation count");
                }
                // this weak reservation is held by the existence of the strong reservation
                assertEquals(1, signedState.getWeakReservations(), "unexpected reservation count");
            }
        }
    }

    /**
     * Build a fake signature. The signature acts like a correct signature for the given key/hash,
     * and acts like an invalid signature for any other key/hash. If either the key or hash is set
     * to null then the signature will always fail (this is sometimes desired).
     */
    protected static Signature buildFakeSignature(final PublicKey key, final Hash hash) {

        final Signature signature = mock(Signature.class);

        when(signature.verifySignature(any(byte[].class), any(PublicKey.class)))
                .thenAnswer(
                        (final InvocationOnMock invocation) -> {
                            final byte[] data = invocation.getArgument(0);
                            final PublicKey publicKey = invocation.getArgument(1);

                            return Arrays.equals(hash.getValue(), data)
                                    && Objects.equals(publicKey, key);
                        });

        return signature;
    }

    /** Build a signature that is always considered to be valid. */
    protected static Signature buildReallyFakeSignature() {
        final Signature signature = mock(Signature.class);

        when(signature.verifySignature(any(byte[].class), any(PublicKey.class)))
                .thenAnswer((final InvocationOnMock invocation) -> true);

        return signature;
    }

    /** Create a HashSigner that always returns valid (fake) signatures. */
    protected static HashSigner buildFakeHashSigner(final PublicKey key) {
        return hash -> buildFakeSignature(key, hash);
    }

    /** Initialize a signed state manager. */
    protected SignedStateManager buildSignedStateManager(
            final DispatchBuilder dispatchBuilder,
            final AddressBook addressBook,
            final long selfId) {

        final SignedStateMetrics metrics = mock(SignedStateMetrics.class);
        when(metrics.getFreshStatesMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStaleStatesMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getTotalUnsignedStatesMetric()).thenReturn(mock(Counter.class));
        when(metrics.getStateSignaturesGatheredPerSecondMetric())
                .thenReturn(mock(SpeedometerMetric.class));
        when(metrics.getStatesSignedPerSecondMetric()).thenReturn(mock(SpeedometerMetric.class));
        when(metrics.getAverageSigningTimeMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getSignedStateHashingTimeMetric())
                .thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateDeletionQueueAvgMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateDeletionTimeAvgMetric()).thenReturn(mock(RunningAverageMetric.class));
        when(metrics.getStateArchivalTimeAvgMetric()).thenReturn(mock(RunningAverageMetric.class));

        final SignedStateManager signedStateManager =
                new SignedStateManager(
                        dispatchBuilder,
                        addressBook,
                        new NodeId(false, selfId),
                        x -> {},
                        buildFakeHashSigner(addressBook.getAddress(selfId).getSigPublicKey()),
                        metrics);
        signedStateManager.start();
        return signedStateManager;
    }
}

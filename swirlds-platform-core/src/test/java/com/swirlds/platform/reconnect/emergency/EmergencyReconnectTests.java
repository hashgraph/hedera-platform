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
package com.swirlds.platform.reconnect.emergency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.AssertionUtils;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.merkle.util.PairedStreams;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.reconnect.DummyConnection;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the emergency reconnect protocol learner and teacher flows. */
public class EmergencyReconnectTests {
    private static final Future<Boolean> trueFuture = mock(Future.class);
    private final RandomSignedStateGenerator signedStateGenerator =
            new RandomSignedStateGenerator();
    private final Crypto crypto = mock(Crypto.class);
    private final NodeId learnerId = new NodeId(false, 0L);
    private final NodeId teacherId = new NodeId(false, 1L);
    private final ReconnectThrottle reconnectThrottle = mock(ReconnectThrottle.class);
    private SignedStateManager signedStateManager = mock(SignedStateManager.class);
    private final ParallelExecutor executor = new CachedPoolParallelExecutor("test-executor");
    private EmergencyReconnectProtocol learnerProtocol;
    private EmergencyReconnectProtocol teacherProtocol;

    @BeforeEach
    public void setup()
            throws ExecutionException, InterruptedException, ConstructableRegistryException {
        ConstructableRegistry.registerConstructables("com.swirlds.common");
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(State.class, State::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(PlatformState.class, PlatformState::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(PlatformData.class, PlatformData::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(Hash.class, Hash::new));

        when(trueFuture.get()).thenReturn(true);
        when(reconnectThrottle.initiateReconnect(anyLong())).thenReturn(true);
    }

    @DisplayName("Verify learner-teacher interaction when teacher does not has a compatible state")
    @Test
    void teacherDoesNotHaveCompatibleState() throws InterruptedException {
        final Hash stateHash = RandomUtils.randomHash();
        final EmergencyRecoveryFile emergencyRecoveryFile =
                new EmergencyRecoveryFile(1L, stateHash);

        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(true);

        learnerProtocol = createLearnerProtocol(emergencyRecoveryFile, reconnectController);
        teacherProtocol = createTeacherProtocol(reconnectController);

        mockTeacherDoesNotHaveCompatibleState();

        executeReconnect();

        assertTeacherSearchedForState(emergencyRecoveryFile);
        assertLearnerDoesNotReconnect(reconnectController);
    }

    @Disabled("Hangs when run in GitHub Actions")
    @DisplayName("Verify learner-teacher interaction when teacher has compatible state")
    @Test
    void teacherHasCompatibleState() throws InterruptedException {
        final Random random = RandomUtils.initRandom(null);
        final int numNodes = 4;
        final List<Long> nodeIds =
                IntStream.range(0, numNodes).mapToLong(i -> (long) i).boxed().toList();
        final long emergencyRound = 1L;

        final AddressBook addressBook = newAddressBook(random, numNodes);

        // Building the signed state takes a weak reservation
        final SignedState teacherState =
                signedStateGenerator
                        .setRound(emergencyRound)
                        .setAddressBook(addressBook)
                        .setSigningNodeIds(nodeIds)
                        .build();

        MerkleCryptoFactory.getInstance().digestSync(teacherState.getState());

        signedStateManager =
                new SignedStateManager(
                        new DispatchBuilder(),
                        addressBook,
                        teacherId,
                        ss -> {},
                        mock(HashSigner.class),
                        mockSignedStateMetrics());
        signedStateManager.addCompleteSignedState(teacherState, SourceOfSignedState.DISK);

        final Hash emergencyStateHash = teacherState.getState().getHash();

        final SignedState learnerState =
                signedStateGenerator
                        .setRound(emergencyRound - 10)
                        .setAddressBook(addressBook)
                        .build();
        learnerState.getState().setHash(RandomUtils.randomHash(random));

        // Make all signatures on the emergency state valid
        when(crypto.verifySignatureParallel(any(), any(), any(), any())).thenReturn(trueFuture);

        final AtomicReference<SignedState> receivedSignedState = new AtomicReference<>();
        final ReconnectController reconnectController =
                createReconnectController(
                        addressBook, learnerState::getState, receivedSignedState::set);
        reconnectController.start();

        // Give the reconnect controller some time to start waiting for the connection before the
        // learner
        // attempts to acquire the provide permit
        TimeUnit.MILLISECONDS.sleep(500);

        final EmergencyRecoveryFile emergencyRecoveryFile =
                new EmergencyRecoveryFile(emergencyRound, emergencyStateHash);

        learnerProtocol = createLearnerProtocol(emergencyRecoveryFile, reconnectController);
        teacherProtocol = createTeacherProtocol(reconnectController);

        //		mockTeacherHasCompatibleState(emergencyRecoveryFile, teacherState);

        AssertionUtils.completeBeforeTimeout(this::executeReconnect, Duration.ofSeconds(5), "");

        checkSignedStateReservations(receivedSignedState.get(), teacherState);
        //		assertTeacherSearchedForState(emergencyRecoveryFile);
        assertLearnerReceivedTeacherState(teacherState, receivedSignedState);
    }

    private SignedStateMetrics mockSignedStateMetrics() {
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
        return metrics;
    }

    private void checkSignedStateReservations(
            final SignedState receivedSignedState, final SignedState teacherState) {
        assertEquals(
                0,
                receivedSignedState.getReservations(),
                "Signed state received by the learner should not have any strong "
                        + "reservations until loaded into the rest of the platform.");
        assertEquals(
                0,
                receivedSignedState.getWeakReservations(),
                "incorrect number of weak reservations on the learner's received state");
        assertEquals(
                2,
                teacherState.getReservations(),
                "incorrect number of strong reservations on the teacher state");
        assertEquals(
                1,
                teacherState.getWeakReservations(),
                "incorrect number of weak reservations on the teacher state");
    }

    private void assertLearnerDoesNotReconnect(final ReconnectController reconnectController)
            throws InterruptedException {
        verify(reconnectController, times(0)).provideLearnerConnection(any(Connection.class));
        verify(reconnectController, times(0)).cancelLearnerPermit();
    }

    private void assertLearnerReceivedTeacherState(
            final SignedState teacherState,
            final AtomicReference<SignedState> receivedSignedState) {
        assertEquals(
                teacherState.getState().getHash(),
                receivedSignedState.get().getState().getHash(),
                "Learner did not receive the teacher's state");
    }

    private void assertTeacherSearchedForState(final EmergencyRecoveryFile emergencyRecoveryFile) {
        verify(
                        signedStateManager,
                        times(1).description("Teacher did not search for the correct state"))
                .find(emergencyRecoveryFile.round(), emergencyRecoveryFile.hash());
    }

    private ReconnectController createReconnectController(
            final AddressBook addressBook,
            final Supplier<State> learnerState,
            final Consumer<SignedState> receivedStateConsumer) {

        final ReconnectHelper helper =
                new ReconnectHelper(
                        () -> {},
                        mock(Clearable.class),
                        learnerState,
                        () -> -1L,
                        mock(ReconnectLearnerThrottle.class),
                        receivedStateConsumer,
                        new ReconnectLearnerFactory(
                                addressBook,
                                mock(ReconnectSettings.class),
                                mock(ReconnectMetrics.class)));

        return new ReconnectController(helper, () -> {});
    }

    private void executeReconnect() {
        try (final PairedStreams pairedStreams = new PairedStreams()) {
            executor.doParallel(
                    AssertionUtils.completeBeforeTimeout(
                            () ->
                                    doTeacher(
                                            teacherProtocol,
                                            new DummyConnection(
                                                    teacherId,
                                                    learnerId,
                                                    pairedStreams.getTeacherInput(),
                                                    pairedStreams.getTeacherOutput())),
                            Duration.ofSeconds(5),
                            ""),
                    AssertionUtils.completeBeforeTimeout(
                            () ->
                                    doLearner(
                                            learnerProtocol,
                                            new DummyConnection(
                                                    learnerId,
                                                    teacherId,
                                                    pairedStreams.getLearnerInput(),
                                                    pairedStreams.getLearnerOutput())),
                            Duration.ofSeconds(5),
                            ""));
        } catch (final IOException | ParallelExecutionException | InterruptedException e) {
            System.out.println(e);
            throw new RuntimeException(e);
        }
    }

    private EmergencyReconnectProtocol createTeacherProtocol(
            final ReconnectController reconnectController) {
        return new EmergencyReconnectProtocol(
                teacherId,
                null,
                reconnectThrottle,
                signedStateManager,
                100,
                mock(ReconnectMetrics.class),
                reconnectController,
                crypto);
    }

    private EmergencyReconnectProtocol createLearnerProtocol(
            final EmergencyRecoveryFile emergencyRecoveryFile,
            final ReconnectController reconnectController) {
        return new EmergencyReconnectProtocol(
                learnerId,
                new AtomicReference<>(emergencyRecoveryFile),
                mock(ReconnectThrottle.class),
                mock(SignedStateManager.class),
                100,
                mock(ReconnectMetrics.class),
                reconnectController,
                mock(Crypto.class));
    }

    private void mockTeacherHasCompatibleState(
            final EmergencyRecoveryFile emergencyRecoveryFile, final SignedState teacherState) {
        when(signedStateManager.find(emergencyRecoveryFile.round(), emergencyRecoveryFile.hash()))
                .thenAnswer(
                        i -> {
                            teacherState.weakReserveState();
                            return new AutoCloseableWrapper<>(
                                    teacherState, teacherState::weakReleaseState);
                        });
    }

    private AddressBook newAddressBook(final Random random, final int numNodes) {
        return new RandomAddressBookGenerator(random)
                .setSize(numNodes)
                .setAverageStake(100L)
                .setStakeDistributionStrategy(
                        RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                .setSequentialIds(true)
                .build();
    }

    private void mockTeacherDoesNotHaveCompatibleState() {
        when(signedStateManager.find(anyLong(), any()))
                .thenReturn(new AutoCloseableWrapper<>(null, () -> {}));
    }

    private Callable<Void> doLearner(
            final EmergencyReconnectProtocol learnerProtocol, final Connection connection) {
        return () -> {
            if (!learnerProtocol.shouldInitiate()) {
                System.out.println("Learner should initiate but will not!");
            }
            try {
                learnerProtocol.runProtocol(connection);
            } catch (final NetworkProtocolException | IOException | InterruptedException e) {
                throw new ParallelExecutionException(e);
            }
            return null;
        };
    }

    private Callable<Boolean> doTeacher(
            final EmergencyReconnectProtocol teacherProtocol, final Connection connection) {
        return () -> {
            if (!teacherProtocol.shouldAccept()) {
                System.out.println("Teacher should accept but will not!");
            }
            try {
                teacherProtocol.runProtocol(connection);
            } catch (final NetworkProtocolException | IOException | InterruptedException e) {
                throw new ParallelExecutionException(e);
            }
            return true;
        };
    }
}

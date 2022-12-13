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
package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.test.AssertionUtils.completeBeforeTimeout;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static java.nio.file.Files.exists;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignedStateFileManager Tests")
class SignedStateFileManagerTests {

    private static final NodeId SELF_ID = new NodeId(false, 1234);
    private static final String MAIN_CLASS_NAME = "com.swirlds.foobar";
    private static final String SWIRLD_NAME = "mySwirld";
    /** Temporary directory provided by JUnit */
    @TempDir Path testDirectory;

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(State.class, State::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(PlatformState.class, PlatformState::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(DualStateImpl.class, DualStateImpl::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(DummySwirldState2.class, DummySwirldState2::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(PlatformData.class, PlatformData::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(AddressBook.class, AddressBook::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(Address.class, Address::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(Hash.class, Hash::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(BasicSoftwareVersion.class, BasicSoftwareVersion::new));
    }

    @BeforeEach
    void beforeEach() throws IOException {
        Settings.getInstance().getState().savedStateDirectory = testDirectory.toFile().toString();
        TemporaryFileBuilder.overrideTemporaryFileLocation(testDirectory);
    }

    @AfterEach
    void afterEach() {
        Settings.getInstance().getState().savedStateDirectory = "data/saved";
    }

    /** Make sure the signed state was properly saved. */
    private void validateSavingOfState(final SignedState originalState) throws IOException {

        final Path stateDirectory =
                getSignedStateDirectory(
                        MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, originalState.getRound());

        validateSavingOfState(originalState, stateDirectory);
    }

    /** Make sure the signed state was properly saved. */
    private void validateSavingOfState(final SignedState originalState, final Path stateDirectory)
            throws IOException {

        assertEquals(0, originalState.getReservations(), "unexpected number of reservations");
        assertEquals(
                1, originalState.getWeakReservations(), "unexpected number of weak reservations");

        final Path stateFile = stateDirectory.resolve(SignedStateFileUtils.SIGNED_STATE_FILE_NAME);
        final Path hashInfoFile = stateDirectory.resolve(SignedStateFileUtils.HASH_INFO_FILE_NAME);
        final Path settingsUsedFile = stateDirectory.resolve("settingsUsed.txt");

        assertTrue(exists(stateFile), "no state file found");
        assertTrue(exists(hashInfoFile), "no hash info file found");
        assertTrue(exists(settingsUsedFile), "no settings used file found");

        assertEquals(0, originalState.getReservations(), "invalid reservation count");
        assertEquals(1, originalState.getWeakReservations(), "invalid weak reservation count");

        final DeserializedSignedState deserializedSignedState = readStateFile(stateFile);
        MerkleCryptoFactory.getInstance()
                .digestTreeSync(deserializedSignedState.signedState().getState());

        assertNotNull(deserializedSignedState.originalHash(), "hash should not be null");
        assertNotSame(
                deserializedSignedState.signedState(),
                originalState,
                "deserialized object should not be the same");

        assertEquals(
                originalState.getState().getHash(),
                deserializedSignedState.signedState().getState().getHash(),
                "hash should match");
        assertEquals(
                originalState.getState().getHash(),
                deserializedSignedState.originalHash(),
                "hash should match");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Standard Operation Test")
    void standardOperationTest(final boolean isFreezeState)
            throws InterruptedException, IOException {

        Settings.getInstance().getState().savedStateDirectory = testDirectory.toFile().toString();

        final SignedState signedState =
                new RandomSignedStateGenerator().setFreezeState(isFreezeState).build();
        assertEquals(isFreezeState, signedState.isFreezeState(), "invalid freeze configuration");

        final AtomicBoolean freezeCallbackInvoked = new AtomicBoolean(false);

        final CountDownLatch latch = new CountDownLatch(1);
        final StateWriteToDiskCompleteListener listener =
                (final StateWriteToDiskCompleteNotification notification) -> {
                    if (signedState.getSwirldState() != notification.getState()) {
                        return;
                    }

                    latch.countDown();
                };
        NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, listener);

        final SignedStateFileManager manager =
                new SignedStateFileManager(
                        MAIN_CLASS_NAME,
                        SELF_ID,
                        SWIRLD_NAME,
                        () -> freezeCallbackInvoked.set(true));
        manager.start();

        manager.saveSignedStateToDisk(signedState);

        completeBeforeTimeout(
                () -> latch.await(), Duration.ofSeconds(1), "latch did not complete on time");

        validateSavingOfState(signedState);

        assertEquals(
                isFreezeState, freezeCallbackInvoked.get(), "freeze callback not handled properly");

        // cleanup
        NotificationFactory.getEngine()
                .unregister(StateWriteToDiskCompleteListener.class, listener);
        manager.stop();
    }

    @Test
    @DisplayName("Save Fatal Signed State")
    void saveFatalSignedState() throws InterruptedException, IOException {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        ((DummySwirldState2) signedState.getSwirldState()).enableBlockingSerialization();

        final AtomicBoolean finished = new AtomicBoolean(false);
        final StateWriteToDiskCompleteListener listener =
                (final StateWriteToDiskCompleteNotification notification) -> {
                    if (signedState.getSwirldState() != notification.getState()) {
                        return;
                    }

                    finished.set(true);
                };
        NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, listener);

        final SignedStateFileManager manager =
                new SignedStateFileManager(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, () -> {});
        manager.start();

        final Thread thread =
                new ThreadConfiguration()
                        .setInterruptableRunnable(
                                () -> manager.dumpState(signedState, "fatal", true))
                        .build(true);

        // State writing should be synchronized. So we shouldn't be able to finish until we unblock.
        MILLISECONDS.sleep(10);
        assertFalse(finished.get(), "shouldn't be able to finish yet");
        assertTrue(thread.isAlive(), "thread should still be blocked");

        ((DummySwirldState2) signedState.getSwirldState()).unblockSerialization();
        thread.join(1000);
        assertTrue(finished.get(), "should be finished");

        final Path stateDirectory =
                testDirectory.resolve("fatal").resolve("node1234_round" + signedState.getRound());
        validateSavingOfState(signedState, stateDirectory);

        // cleanup
        NotificationFactory.getEngine()
                .unregister(StateWriteToDiskCompleteListener.class, listener);
        manager.stop();
    }

    @Test
    @DisplayName("Save ISS Signed State")
    void saveISSignedState() throws IOException {

        final SignedState signedState = new RandomSignedStateGenerator().build();

        final AtomicBoolean finished = new AtomicBoolean(false);
        final StateWriteToDiskCompleteListener listener =
                (final StateWriteToDiskCompleteNotification notification) -> {
                    if (signedState.getSwirldState() != notification.getState()) {
                        return;
                    }

                    finished.set(true);
                };
        NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, listener);

        final SignedStateFileManager manager =
                new SignedStateFileManager(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, () -> {});
        manager.start();

        manager.dumpState(signedState, "iss", false);

        assertEventuallyTrue(
                finished::get, Duration.ofSeconds(1), "should eventually be written to disk");

        final Path stateDirectory =
                testDirectory.resolve("iss").resolve("node1234_round" + signedState.getRound());
        validateSavingOfState(signedState, stateDirectory);

        // cleanup
        NotificationFactory.getEngine()
                .unregister(StateWriteToDiskCompleteListener.class, listener);
        manager.stop();
    }

    /**
     * Helper method for {@link #maxCapacityTest()}. Add states to the queue, which will eventually
     * become blocked.
     */
    private void addToQueue(
            final SignedStateFileManager manager,
            final int stateIndex,
            final SignedState state,
            final int queueSize) {

        if (stateIndex < queueSize + 1) {
            // Note that it's actually queueSize + 1. This is because one state will have been
            // removed
            // from the queue for handling.
            assertTrue(manager.saveSignedStateToDisk(state), "queue should have capacity");

            assertEquals(0, state.getReservations(), "incorrect reservation count");
            assertEquals(
                    2, state.getWeakReservations(), "state should have an extra weak reservation");
        } else {
            assertFalse(manager.saveSignedStateToDisk(state), "queue should be full");
            assertEquals(0, state.getReservations(), "incorrect reservation count");
            assertEquals(1, state.getWeakReservations(), "incorrect weak reservation count");
        }

        if (stateIndex == 0) {
            // Special case: wait for the background thread to take the first element out of the
            // queue.
            // Avoids possible test race condition.
            assertEventuallyEquals(
                    0,
                    manager::getTaskQueueSize,
                    Duration.ofSeconds(1),
                    "first item should eventually be removed from queue");
        } else if (stateIndex < queueSize + 1) {
            assertEquals(stateIndex, manager.getTaskQueueSize(), "incorrect queue size");
        } else {
            assertEquals(queueSize, manager.getTaskQueueSize(), "incorrect queue size");
        }
    }

    /**
     * Helper method for {@link #maxCapacityTest()}. Unblock serialization for one of the states.
     */
    private void unblockSerialization(
            final SignedStateFileManager manager,
            final int stateIndex,
            final SignedState state,
            final int queueSize,
            final AtomicInteger statesWritten) {

        if (stateIndex < queueSize + 1) {
            ((DummySwirldState2) state.getSwirldState()).unblockSerialization();
            assertEventuallyEquals(
                    stateIndex + 1,
                    statesWritten::get,
                    Duration.ofSeconds(1),
                    "state should eventually be saved");
            assertEventuallyEquals(
                    Math.max(0, queueSize - stateIndex - 1),
                    manager::getTaskQueueSize,
                    Duration.ofSeconds(1),
                    "queue should eventually shrink");
        } else {
            assertEquals(queueSize + 1, statesWritten.get(), "no more states should be written");
            assertEquals(0, manager.getTaskQueueSize(), "queue should remain empty");
        }
    }

    @Test
    @DisplayName("Max Capacity Test")
    void maxCapacityTest() {

        final AtomicInteger statesWritten = new AtomicInteger(0);
        final StateWriteToDiskCompleteListener listener =
                notification -> statesWritten.getAndIncrement();
        NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, listener);

        final int queueSize = 5;
        Settings.getInstance().getState().stateSavingQueueSize = queueSize;

        final SignedStateFileManager manager =
                new SignedStateFileManager(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, () -> {});
        manager.start();

        final List<SignedState> states = new ArrayList<>();
        for (int i = 0; i < queueSize * 2; i++) {
            final SignedState signedState = new RandomSignedStateGenerator().build();
            ((DummySwirldState2) signedState.getSwirldState()).enableBlockingSerialization();
            states.add(signedState);
        }

        // Add things to the queue. Serialization will block, causing the manager to become stuck.
        for (int stateIndex = 0; stateIndex < queueSize * 2; stateIndex++) {
            addToQueue(manager, stateIndex, states.get(stateIndex), queueSize);
        }

        // Unblock serialization for one state at a time.
        for (int stateIndex = 0; stateIndex < queueSize * 2; stateIndex++) {
            unblockSerialization(
                    manager, stateIndex, states.get(stateIndex), queueSize, statesWritten);
        }

        // All states should have the correct reference count, regardless of serialization status.
        assertEventuallyDoesNotThrow(
                () -> {
                    for (final SignedState signedState : states) {
                        assertEquals(
                                0, signedState.getReservations(), "incorrect reservation count");
                        assertEquals(
                                1,
                                signedState.getWeakReservations(),
                                "incorrect weak reservation count");
                    }
                },
                Duration.ofSeconds(1),
                "all reference counts should have been released by now");

        // cleanup
        NotificationFactory.getEngine()
                .unregister(StateWriteToDiskCompleteListener.class, listener);
        manager.stop();
    }

    /**
     * Simulate a sequence of states where a state is saved periodically. Ensure that the proper
     * states are saved, and ensure that states on disk are deleted when they get too old.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Sequence Of States Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void sequenceOfStatesTest(final boolean startAtGenesis) {

        final Random random = getRandomPrintSeed();

        // Save state every 100 (simulated) seconds
        final int stateSavePeriod = 100;
        Settings.getInstance().getState().saveStatePeriod = stateSavePeriod;
        final int statesOnDisk = 3;
        Settings.getInstance().getState().signedStateDisk = statesOnDisk;

        final int totalStates = 1000;
        final int averageTimeBetweenStates = 10;
        final double standardDeviationTimeBetweenStates = 0.5;

        final SignedStateFileManager manager =
                new SignedStateFileManager(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, () -> {});
        manager.start();

        Instant timestamp;
        final long firstRound;
        Instant nextBoundary;
        final List<SignedState> savedStates = new ArrayList<>();

        if (startAtGenesis) {
            timestamp = Instant.EPOCH;
            firstRound = 0;
            nextBoundary = null;
        } else {
            firstRound = random.nextInt(1000);
            timestamp = Instant.ofEpochSecond(random.nextInt(1000));

            final SignedState initialState =
                    new RandomSignedStateGenerator(random)
                            .setConsensusTimestamp(timestamp)
                            .setRound(firstRound)
                            .build();
            savedStates.add(initialState);
            manager.registerSignedStateFromDisk(initialState);

            nextBoundary =
                    Instant.ofEpochSecond(
                            timestamp.getEpochSecond() / stateSavePeriod * stateSavePeriod
                                    + stateSavePeriod);
        }

        for (long round = firstRound; round < totalStates + firstRound; round++) {

            final int secondsDelta =
                    (int)
                            Math.max(
                                    1,
                                    random.nextGaussian() * standardDeviationTimeBetweenStates
                                            + averageTimeBetweenStates);

            timestamp = timestamp.plus(secondsDelta, ChronoUnit.SECONDS);

            final SignedState signedState =
                    new RandomSignedStateGenerator(random)
                            .setConsensusTimestamp(timestamp)
                            .setRound(round)
                            .build();

            manager.determineIfStateShouldBeSaved(signedState);

            if (signedState.isStateToSave()) {
                assertTrue(
                        nextBoundary == null
                                || CompareTo.isGreaterThanOrEqualTo(timestamp, nextBoundary),
                        "timestamp should be after the boundary");

                savedStates.add(signedState);
                manager.saveSignedStateToDisk(signedState);

                assertEventuallyDoesNotThrow(
                        () -> {
                            rethrowIO(() -> validateSavingOfState(signedState));

                            final SavedStateInfo[] currentStatesOnDisk =
                                    SignedStateFileReader.getSavedStateFiles(
                                            MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

                            assertTrue(
                                    currentStatesOnDisk.length <= statesOnDisk,
                                    "unexpected number of states on disk, current number = "
                                            + currentStatesOnDisk.length);

                            for (int index = 0; index < currentStatesOnDisk.length; index++) {

                                final SavedStateInfo savedStateInfo = currentStatesOnDisk[index];

                                final SignedState stateFromDisk =
                                        assertDoesNotThrow(
                                                () ->
                                                        SignedStateFileReader.readStateFile(
                                                                        savedStateInfo.stateFile())
                                                                .signedState(),
                                                "should be able to read state on disk");

                                final SignedState originalState =
                                        savedStates.get(savedStates.size() - index - 1);
                                assertEquals(
                                        originalState.getRound(),
                                        stateFromDisk.getRound(),
                                        "round should match");
                                assertEquals(
                                        originalState.getConsensusTimestamp(),
                                        stateFromDisk.getConsensusTimestamp(),
                                        "timestamp should match");
                            }
                        },
                        Duration.ofSeconds(2),
                        "state saving should have wrapped up by now");

                // The first state with a timestamp after this boundary should be saved
                nextBoundary =
                        Instant.ofEpochSecond(
                                timestamp.getEpochSecond() / stateSavePeriod * stateSavePeriod
                                        + stateSavePeriod);
            } else {
                assertNotNull(
                        nextBoundary,
                        "if the next boundary is null then the state should have been saved");
                assertTrue(
                        CompareTo.isGreaterThan(nextBoundary, timestamp),
                        "next boundary should be after current timestamp");
            }
        }
    }

    @SuppressWarnings("resource")
    @Test
    @DisplayName("State Deletion Test")
    void stateDeletionTest() throws IOException, InterruptedException {
        final Random random = new Random();

        final int statesOnDisk = 3;
        Settings.getInstance().getState().signedStateDisk = statesOnDisk;
        final int count = 10;

        final SignedStateFileManager manager =
                new SignedStateFileManager(MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME, () -> {});
        manager.start();

        final Path statesDirectory =
                SignedStateFileUtils.getSignedStatesDirectoryForSwirld(
                        MAIN_CLASS_NAME, SELF_ID, SWIRLD_NAME);

        // Simulate the saving of an ISS state
        final int issRound = 666;
        final Path issDirectory =
                getSignedStatesBaseDirectory()
                        .resolve("iss")
                        .resolve("node" + SELF_ID + "_round" + issRound);
        final SignedState issState =
                new RandomSignedStateGenerator(random).setRound(issRound).build();
        manager.dumpState(issState, "iss", false);
        assertEventuallyDoesNotThrow(
                () -> {
                    try {
                        validateSavingOfState(issState, issDirectory);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                Duration.ofSeconds(1),
                "ISS state should have been written by now");

        // Simulate the saving of a fatal state
        final int fatalRound = 667;
        final Path fatalDirectory =
                getSignedStatesBaseDirectory()
                        .resolve("fatal")
                        .resolve("node" + SELF_ID + "_round" + fatalRound);
        final SignedState fatalState =
                new RandomSignedStateGenerator(random).setRound(fatalRound).build();
        manager.dumpState(fatalState, "fatal", true);
        validateSavingOfState(fatalState, fatalDirectory);

        // Save a bunch of states. After each time, check the states that are still on disk.
        final List<SignedState> states = new ArrayList<>();
        for (int round = 0; round < count; round++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random).setRound(round).build();
            states.add(signedState);
            manager.saveSignedStateToDisk(signedState);

            // Verify that the states we want to be on disk are still on disk
            for (int i = 0; i < statesOnDisk; i++) {
                final int roundToValidate = round - i;
                if (roundToValidate < 0) {
                    continue;
                }
                // State saving happens asynchronously, so don't expect completion immediately
                assertEventuallyDoesNotThrow(
                        () -> {
                            try {
                                validateSavingOfState(states.get(roundToValidate));
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        },
                        Duration.ofSeconds(1),
                        "state should still be on disk");
            }

            // Verify that old states are properly deleted
            assertEventuallyEquals(
                    Math.min(statesOnDisk, round + 1),
                    () -> {
                        try {
                            return (int) Files.list(statesDirectory).count();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    Duration.ofSeconds(1),
                    "incorrect number of state files");

            // ISS/fatal state should still be in place
            validateSavingOfState(issState, issDirectory);
            validateSavingOfState(fatalState, fatalDirectory);
        }
    }
}

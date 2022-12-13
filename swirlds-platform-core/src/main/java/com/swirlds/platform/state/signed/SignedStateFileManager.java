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
package com.swirlds.platform.state.signed;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeSignedStateToDisk;

import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.Uninterruptable;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.Settings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This class is responsible for managing the signed state writing pipeline. */
public class SignedStateFileManager implements Startable {

    private static final Logger LOG = LogManager.getLogger(SignedStateFileManager.class);

    /**
     * A runnable that indicates the freeze state has been written to disk and the freeze is
     * complete.
     */
    private final Runnable freezeComplete;

    /**
     * The timestamp of the signed state that was most recently written to disk, or null if no
     * timestamp was recently written to disk.
     */
    private Instant previousSavedStateTimestamp;

    private final AtomicLong lastRoundSavedToDisk = new AtomicLong(-1);

    /** The ID of this node. */
    private final NodeId selfId;

    /** The name of the application that is currently running. */
    private final String mainClassName;

    /** The swirld name. */
    private final String swirldName;

    /** A background queue of tasks. */
    private final QueueThread<Runnable> taskQueue;

    /**
     * Creates a new instance.
     *
     * @param mainClassName the main class name of this node
     * @param selfId the ID of this node
     * @param swirldName the name of the swirld
     * @param freezeComplete a runnable that tells the system a freeze is complete
     */
    public SignedStateFileManager(
            final String mainClassName,
            final NodeId selfId,
            final String swirldName,
            final Runnable freezeComplete) {

        this.selfId = selfId;
        this.mainClassName = mainClassName;
        this.swirldName = swirldName;
        this.freezeComplete = freezeComplete;

        this.taskQueue =
                new QueueThreadConfiguration<Runnable>()
                        .setCapacity(Settings.getInstance().getState().stateSavingQueueSize)
                        .setMaxBufferSize(1)
                        .setPriority(Settings.getInstance().getThreadPriorityNonSync())
                        .setNodeId(selfId.getId())
                        .setComponent(PLATFORM_THREAD_POOL_NAME)
                        .setThreadName("signed-state-file-manager")
                        .setHandler(Runnable::run)
                        .build();
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        taskQueue.start();
    }

    /**
     * Stops the background thread.
     *
     * <p><strong>For unit testing purposes only.</strong>
     */
    public void stop() {
        taskQueue.stop();
    }

    /**
     * Get the number of enqueued state saving tasks. The number returned here will not reflect a
     * state saving task that is currently in progress.
     */
    public int getTaskQueueSize() {
        return taskQueue.size();
    }

    /**
     * Notify signed state listeners that a new state has been saved.
     *
     * @param signedState the state that was saved
     * @param savedStateDirectory the directory that contains the saved state
     */
    private static void notifySavedStateListeners(
            final SignedState signedState, final Path savedStateDirectory) {
        NotificationFactory.getEngine()
                .dispatch(
                        StateWriteToDiskCompleteListener.class,
                        new StateWriteToDiskCompleteNotification(
                                signedState.getRound(),
                                signedState.getConsensusTimestamp(),
                                signedState.getSwirldState(),
                                savedStateDirectory,
                                signedState.isFreezeState()));
    }

    /**
     * Notifies the platform that the signed state is complete, the platform will then write it to a
     * file.
     *
     * <p>This method will take a weak reservation on the signed state before returning, and will
     * eventually release that weak reservation when the state has been fully written to disk (or if
     * state saving fails).
     *
     * @param signedState the complete signed state
     * @param directory the directory where the signed state will be written
     * @param taskDescription a human-readable description of the operation being performed
     * @param finishedCallback a function that is called after state writing is complete. Is passed
     *     true if writing succeeded, else is passed false.
     * @return true if it will be written to disk, false otherwise
     */
    private boolean saveSignedStateToDisk(
            final SignedState signedState,
            final Path directory,
            final String taskDescription,
            final Consumer<Boolean> finishedCallback) {

        signedState.weakReserveState();

        final boolean accepted =
                taskQueue.offer(
                        () -> {
                            boolean success = false;
                            try {
                                writeSignedStateToDisk(directory, signedState, taskDescription);
                                notifySavedStateListeners(signedState, directory);
                                success = true;
                            } catch (final Throwable e) {
                                LOG.error(
                                        EXCEPTION.getMarker(),
                                        "Unable to write signed state to disk for round {} to {}.",
                                        signedState.getRound(),
                                        directory,
                                        e);
                            } finally {
                                if (signedState.isFreezeState()) {
                                    freezeComplete.run();
                                }
                                signedState.weakReleaseState();
                                if (finishedCallback != null) {
                                    finishedCallback.accept(success);
                                }
                            }
                        });

        if (!accepted) {
            signedState.weakReleaseState();
            if (finishedCallback != null) {
                finishedCallback.accept(false);
            }
            LOG.error(
                    STATE_TO_DISK.getMarker(),
                    "Unable to save signed state to disk for round {} due to backlog of "
                            + "operations in the SignedStateManager task queue.",
                    signedState.getRound());
        }
        return accepted;
    }

    /**
     * Save a signed state to disk. This method will be called periodically under standard
     * operations.
     *
     * @param signedState the signed state to be written to disk.
     */
    public boolean saveSignedStateToDisk(final SignedState signedState) {
        return saveSignedStateToDisk(
                signedState,
                getSignedStateDir(signedState.getRound()),
                "periodic snapshot",
                success -> {
                    if (success) {
                        lastRoundSavedToDisk.set(signedState.getRound());
                        deleteOldStates();
                    }
                });
    }

    /**
     * Dump a state to disk out of band.
     *
     * @param signedState the signed state to write todisk
     * @param reason the reason why the state is being written, e.g. "fatal" or "iss". This string
     *     us used as a part of a file path, so it should not contain whitespace or special
     *     characters.
     * @param blocking if true then block until the state has been fully written to disk
     */
    public void dumpState(
            final SignedState signedState, final String reason, final boolean blocking) {
        final CountDownLatch latch = new CountDownLatch(1);

        saveSignedStateToDisk(
                signedState,
                getSignedStatesBaseDirectory()
                        .resolve(reason)
                        .resolve(
                                String.format(
                                        "node%d_round%d", selfId.getId(), signedState.getRound())),
                reason,
                success -> latch.countDown());

        if (blocking) {
            Uninterruptable.abortAndLogIfInterrupted(
                    latch::await,
                    "interrupted while waiting for state dump to complete, "
                            + "state dump may not be completed");
        }
    }

    /**
     * Get the directory for a particular signed state. This directory might not exist
     *
     * @param round the round number for the signed state
     * @return the File that represents the directory of the signed state for the particular round
     */
    private Path getSignedStateDir(final long round) {
        return getSignedStateDirectory(mainClassName, selfId, swirldName, round);
    }

    /**
     * The first round after genesis should be saved to disk and every round which is about
     * saveStatePeriod seconds after the previous one should be saved. This will not always be
     * exactly saveStatePeriod seconds after the previous one, but it will be predictable at what
     * time each a state will be saved
     *
     * @param signedState the state in question
     * @param previousTimestamp the timestamp of the previous state that was saved to disk, or null
     *     if no previous state was saved to disk
     * @return true if the state should be written to disk
     */
    private static boolean shouldSaveToDisk(
            final SignedState signedState, final Instant previousTimestamp) {
        if (signedState.isFreezeState()) {
            // the state right before a freeze should be written to disk
            return true;
        }

        final int saveStatePeriod = Settings.getInstance().getState().getSaveStatePeriod();
        if (saveStatePeriod <= 0) {
            // state saving is disabled
            return false;
        }

        if (previousTimestamp == null) {
            // the first round should be saved
            return true;
        }

        return (signedState.getConsensusTimestamp().getEpochSecond() / saveStatePeriod)
                > (previousTimestamp.getEpochSecond() / saveStatePeriod);
    }

    /**
     * Determine if a signed state should eventually be written to disk. If the state should
     * eventually be written, the state's {@link SignedState#isStateToSave()} flag will be set to
     * true.
     *
     * @param signedState the signed state in question
     */
    public void determineIfStateShouldBeSaved(final SignedState signedState) {
        if (shouldSaveToDisk(signedState, previousSavedStateTimestamp)) {

            LOG.info(
                    STATE_TO_DISK.getMarker(),
                    "Signed state from round {} created, will eventually be written to disk once"
                            + " sufficient signatures are collected",
                    signedState.getRound());

            previousSavedStateTimestamp = signedState.getConsensusTimestamp();
            signedState.setStateToSave(true);
        }
    }

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    public synchronized void registerSignedStateFromDisk(final SignedState signedState) {
        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
        lastRoundSavedToDisk.set(signedState.getRound());
    }

    /** Purge old states on the disk. */
    private synchronized void deleteOldStates() {
        final SavedStateInfo[] savedStates = getSavedStateFiles(mainClassName, selfId, swirldName);

        // States are returned newest to oldest. So delete from the end of the list to delete the
        // oldest states.
        for (int index = savedStates.length - 1;
                index >= Settings.getInstance().getState().getSignedStateDisk();
                index--) {
            final SavedStateInfo savedStateInfo = savedStates[index];
            try {
                deleteDirectoryAndLog(savedStateInfo.getDir());
            } catch (final IOException e) {
                // Intentionally ignored, deleteDirectoryAndLog will log any exceptions that happen
            }
        }
    }

    /**
     * Get the last round that was saved to disk.
     *
     * @return the last round that was saved to disk, or -1 if no round was recently saved to disk
     */
    public long getLastRoundSavedToDisk() {
        return lastRoundSavedToDisk.get();
    }
}

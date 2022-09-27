/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.state.signed;

import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.platform.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStatesBaseDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.getSignedStateDirectory;
import static com.swirlds.platform.state.signed.SignedStateFileWriter.writeSignedStateToDisk;

/**
 * This class is responsible for managing the signed state writing pipeline.
 */
public class SignedStateFileManager {

	private static final Logger LOG = LogManager.getLogger(SignedStateFileManager.class);

	/** A runnable that indicates the freeze state has been written to disk and the freeze is complete. */
	private final Runnable freezeComplete;

	/**
	 * The ID of this node.
	 */
	private final NodeId selfId;

	/**
	 * The name of the application that is currently running.
	 */
	private final String mainClassName;

	/**
	 * The swirld name.
	 */
	private final String swirldName;

	private final QueueThread<Runnable> taskQueue;

	/**
	 * Creates a new instance.
	 *
	 * @param mainClassName
	 * 		the main class name of this node
	 * @param selfId
	 * 		the ID of this node
	 * @param swirldName
	 * 		the name of the swirld
	 * @param freezeComplete
	 * 		a runnable that tells the system a freeze is complete
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

		this.taskQueue = new QueueThreadConfiguration<Runnable>()
				.setCapacity(Settings.state.stateSavingQueueSize)
				.setMaxBufferSize(1)
				.setPriority(Settings.threadPriorityNonSync)
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("signed-state-file-manager")
				.setHandler(Runnable::run)
				.build(true);
	}

	/**
	 * Stops the background thread.
	 * <p>
	 * <strong>For unit testing purposes only.</strong>
	 */
	public void stop() {
		taskQueue.stop();
	}

	/**
	 * Get the number of enqueued state saving tasks. The number returned here will not reflect a state
	 * saving task that is currently in progress.
	 */
	public int getTaskQueueSize() {
		return taskQueue.size();
	}

	/**
	 * Notify signed state listeners that a new state has been saved.
	 *
	 * @param signedState
	 * 		the state that was saved
	 * @param savedStateDirectory
	 * 		the directory that contains the saved state
	 */
	private static void notifySavedStateListeners(final SignedState signedState, final Path savedStateDirectory) {
		NotificationFactory.getEngine().dispatch(
				StateWriteToDiskCompleteListener.class,
				new StateWriteToDiskCompleteNotification(
						signedState.getLastRoundReceived(),
						signedState.getConsensusTimestamp(),
						signedState.getSwirldState(),
						savedStateDirectory,
						signedState.isFreezeState()
				)
		);
	}

	/**
	 * <p>
	 * Notifies the platform that the signed state is complete, the platform will then write it to a file.
	 * </p>
	 *
	 * <p>
	 * This method will take a weak reservation on the signed state before returning, and will eventually release
	 * that weak reservation when the state has been fully written to disk (or if state saving fails).
	 * </p>
	 *
	 * @param signedState
	 * 		the complete signed state
	 * @param directory
	 * 		the directory where the signed state will be written
	 * @param taskDescription
	 * 		a human-readable description of the operation being performed
	 * @param finishedCallback
	 * 		a function that is called after state writing is complete (called even if state writing fails),
	 * 		ignored if null
	 * @return true if it will be written to disk, false otherwise
	 */
	private boolean saveSignedStateToDisk(
			final SignedState signedState,
			final Path directory,
			final String taskDescription,
			final Runnable finishedCallback) {

		signedState.weakReserveState();

		final boolean accepted = taskQueue.offer(() -> {
			try {
				writeSignedStateToDisk(directory, signedState, taskDescription);
				notifySavedStateListeners(signedState, directory);
				if (signedState.isFreezeState()) {
					freezeComplete.run();
				}
			} catch (final Throwable e) {
				LOG.error(EXCEPTION.getMarker(),
						"Unable to write signed state to disk for round {} to {}.",
						signedState.getLastRoundReceived(), directory, e);
			} finally {
				signedState.weakReleaseState();
				if (finishedCallback != null) {
					finishedCallback.run();
				}
			}
		});

		if (!accepted) {
			signedState.weakReleaseState();
			if (finishedCallback != null) {
				finishedCallback.run();
			}
			LOG.error(STATE_TO_DISK.getMarker(),
					"Unable to save signed state to disk for round {} due to backlog of " +
							"operations in the SignedStateManager task queue.",
					signedState.getLastRoundReceived());
		}

		return accepted;
	}

	/**
	 * Save a signed state to disk. This method will called periodically under standard operations.
	 *
	 * @param signedState
	 * 		the signed state to be written to disk.
	 * @return true if it will be written to disk, false otherwise
	 */
	public boolean saveSignedStateToDisk(final SignedState signedState) {
		return saveSignedStateToDisk(
				signedState,
				getSignedStateDir(signedState.getLastRoundReceived()),
				"periodic snapshot",
				this::deleteOldStates);
	}

	/**
	 * Saves a signed state to disk that had an ISS.
	 *
	 * Ideally this would not be a separate method, but there wasn't a trivial way to change the directory of the
	 * snapshot, so the snapshot is not included in the ISS.
	 *
	 * @param signedState
	 * 		the signed state to be saved which has an ISS
	 * @return true if it will be written to disk, false otherwise
	 */
	public boolean saveIssStateToDisk(final SignedState signedState) {
		return saveSignedStateToDisk(
				signedState,
				getSignedStatesBaseDirectory()
						.resolve("iss")
						.resolve(String.format("node%d_round%d", selfId.getId(), signedState.getLastRoundReceived())),
				"emergency ISS dump",
				null);
	}

	/**
	 * Saves a signed state to disk in response to a fatal exception. Blocks until completed.
	 *
	 * @param signedState
	 * 		the state to save
	 * @return true if it will be written to disk, false otherwise
	 */
	public boolean saveFatalStateToDisk(final SignedState signedState) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final boolean accepted = saveSignedStateToDisk(
				signedState,
				getSignedStatesBaseDirectory()
						.resolve("fatal")
						.resolve(String.format("node%d_round%d", selfId.getId(), signedState.getLastRoundReceived())),
				"emergency dump after fatal exception",
				latch::countDown);

		latch.await();
		return accepted;
	}

	/**
	 * Get the directory for a particular signed state. This directory might not exist
	 *
	 * @param round
	 * 		the round number for the signed state
	 * @return the File that represents the directory of the signed state for the particular round
	 */
	private Path getSignedStateDir(long round) {
		return getSignedStateDirectory(mainClassName, selfId, swirldName, round);
	}

	/**
	 * Purge old states on the disk.
	 */
	private synchronized void deleteOldStates() {
		final SavedStateInfo[] savedStates = getSavedStateFiles(mainClassName, selfId, swirldName);

		// States are returned newest to oldest. So delete from the end of the list to delete the oldest states.
		for (int index = savedStates.length - 1; index >= Settings.state.getSignedStateDisk(); index--) {
			final SavedStateInfo savedStateInfo = savedStates[index];
			try {
				deleteDirectoryAndLog(savedStateInfo.getDir());
			} catch (final IOException e) {
				// Intentionally ignored, deleteDirectoryAndLog will log any exceptions that happen
			}
		}
	}
}

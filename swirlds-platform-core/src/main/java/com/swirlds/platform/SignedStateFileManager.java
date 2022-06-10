/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.state.LocalStateEvents;
import com.swirlds.platform.state.SavedStateInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static com.swirlds.common.io.streams.StreamDebugUtils.deserializeAndDebugOnFailure;
import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.PlatformState.getInfoString;
import static com.swirlds.platform.system.SystemExitReason.SAVED_STATE_NOT_LOADED;
import static com.swirlds.platform.system.SystemUtils.exitSystem;

public class SignedStateFileManager implements Runnable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** The signed state file was not versioned before, this byte was introduced to mark a versioned file */
	private static final byte VERSIONED_FILE_BYTE = Byte.MAX_VALUE;

	/** The current version of the signed state file */
	private static final int FILE_VERSION = 1;

	private static final int MAX_MERKLE_NODES_IN_STATE = Integer.MAX_VALUE;

	private static final String HASH_INFO_FILE_NAME = "hashInfo.txt";

	/** task queue that is polled forever */
	private final BlockingQueue<FileManagerTask> taskQueue = new LinkedBlockingQueue<>(20);

	/** Reference to the platform */
	private final AbstractPlatform platform;

	SignedStateFileManager(AbstractPlatform platform) {
		this.platform = platform;
	}

	/**
	 * Polls the queue of tasks and executes them
	 */
	@Override
	public void run() {
		while (true) {
			FileManagerTask task = null;
			try {
				task = taskQueue.take();

				switch (task.operation) {
					case WRITE:
						writeSignedStateToDisk(task.signedState, task.description, task.getDir());
						break;
					case DELETE:
						deleteRecursively(task.getDir());
						break;
					default:
						log.error(EXCEPTION.getMarker(),
								"Error in SignedStateFileManager.run(), unknown task: {}",
								task.operation.toString());
						break;
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception in SignedStateFileManager.run():", e);
			} finally {
				if (task != null) {
					task.finish();
				}
			}
		}

	}

	/**
	 * Write a file that contains information about the hash of the state. A useful nugget of information
	 * for when a human needs to decide what is contained within a signed state file. If the file already
	 * exists in the given directory then it is overwritten.
	 *
	 * @param state
	 * 		the state that is being written
	 * @param dir
	 * 		the directory where the state is being written
	 */
	private static void writeHashInfoFile(final State state, final File dir) {
		final String platformInfo = getInfoString(state.getPlatformState());
		final String hashInfo = generateHashDebugString(state, StateSettings.getDebugHashDepth());
		log.info(STATE_TO_DISK.getMarker(), "Information for state written to disk:\n{}\n{}", platformInfo,
				hashInfo);

		final File hashInfoFile = CommonUtils.canonicalFile(dir, HASH_INFO_FILE_NAME);

		try (final BufferedWriter writer = new BufferedWriter(new FileWriter(hashInfoFile));) {
			writer.write(platformInfo);
			writer.newLine();
			writer.write(hashInfo);
			writer.flush();
		} catch (final IOException e) {
			log.error(EXCEPTION.getMarker(), "Unable to write hash info file", e);
		}
	}

	/**
	 * Writes a SignedState to a file
	 *
	 * @param signedState
	 * 		the object to be written. Will have already been reserved for archival.
	 * @param taskDescription
	 * 		a description of the task
	 * @param dir
	 * 		the directory where the state will be stored
	 * @throws IOException
	 * 		if there is any problems with writing to a file
	 */
	void writeSignedStateToDisk(final SignedState signedState,
			final String taskDescription, File dir) throws IOException {

		try {
			log.info(STATE_TO_DISK.getMarker(), "Started writing '{}' to disk", taskDescription);

			// we need to create the directories where the file should be stored if it doesn't exist
			if (!dir.mkdirs()) {
				throw new IOException(
						"Directory '" + dir.getAbsolutePath() + "' could not be created!");
			}

			// the files we need to create
			File stateFile = getSavedStateFile(dir, SignedStateFileType.WHOLE_STATE);
			File events = getSavedStateFile(dir, SignedStateFileType.EVENTS);

			// the temp files we will first write to and then rename
			File tmpStateFile = getSavedStateFile(dir, SignedStateFileType.WHOLE_STATE, true);
			File tmpEvents = getSavedStateFile(dir, SignedStateFileType.EVENTS, true);

			throwIfExists(stateFile, tmpStateFile, events, tmpEvents);

			try {
				writeAndRename(dir, stateFile, tmpStateFile, out -> {
					out.write(VERSIONED_FILE_BYTE);
					out.writeInt(FILE_VERSION);
					out.writeProtocolVersion();
					out.writeMerkleTree(signedState.getState());
					out.writeSerializable(signedState.getState().getHash(), true);
					out.writeSerializable(signedState.getSigSet(), true);
				});

				log.info(STATE_TO_DISK.getMarker(),
						"Done writing saved state with HashEventsCons {}, starting with local events",
						signedState::getHashEventsCons);

				writeHashInfoFile(signedState.getState(), dir);

				if (Settings.state.saveLocalEvents) {
					writeAndRename(dir, events, tmpEvents, out -> {
						out.writeInt(FILE_VERSION);
						out.writeProtocolVersion();
						out.writeSerializable(signedState.getLocalStateEvents(), true);
					});
				}

				Settings.writeSettingsUsed(dir);
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception when writing the signed state for round {} to disk:",
						signedState.getLastRoundReceived(), e);
				return;
			}

			// Notify any registered listeners that we have written a signed state to disk
			NotificationFactory.getEngine().dispatch(
					StateWriteToDiskCompleteListener.class,
					new StateWriteToDiskCompleteNotification(
							signedState.getLastRoundReceived(),
							signedState.getConsensusTimestamp(),
							signedState.getSwirldState(),
							dir,
							signedState.isFreezeState()
					)
			);
		} finally {
			// release the signed state if it was reserved
			// set it to saved to disk in all cases so that it can be deleted from memory
			signedState.setSavedToDisk(true);
			signedState.weakReleaseState();
		}


		if (signedState.isFreezeState()) {
			log.info(STATE_TO_DISK.getMarker(), "Finished writing during freeze '{}' to disk", taskDescription);
		} else {
			log.info(STATE_TO_DISK.getMarker(), "Finished writing '{}' to disk", taskDescription);
		}
	}

	public static void writeAndRename(
			final File directory,
			final File file,
			final File tmpFile,
			WritingConsumer<MerkleDataOutputStream> writeMethod) throws Exception {

		try (FileOutputStream fileOut = new FileOutputStream(tmpFile);
			 BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
			 MerkleDataOutputStream out = new MerkleDataOutputStream(bufOut)
					 .setExternal(true)
					 .setExternalDirectory(directory)) {

			writeMethod.write(out);

			// flush all the data to the file stream
			out.flush();
			// make sure the data is actually written to disk
			fileOut.getFD().sync();
		}

		if (!tmpFile.renameTo(file)) {
			throw new Exception(
					"Cannot rename temp file '" +
							tmpFile.getAbsolutePath() +
							"' to '" +
							file.getAbsolutePath() +
							"'"
			);
		}
	}

	private void throwIfExists(File... files) throws IOException {
		for (File file : files) {
			if (file.exists()) {
				throw new IOException(
						"File " + file.getAbsolutePath() + " already exists!");
			}
		}
	}

	/**
	 * Reads a SignedState from a file
	 *
	 * @param file
	 * 		the file to read from
	 * @return a {@link Pair} containing the original {@link Hash} and the {@link SignedState} that were read from disk
	 * @throws IOException
	 * 		if there is any problems with reading from a file
	 */
	public static Pair<Hash, SignedState> readSignedStateFromFile(final File file)
			throws IOException {
		if (!file.exists()) {
			throw new IOException(
					"File " + file.getAbsolutePath() + " does not exist!");
		}
		if (!file.isFile()) {
			throw new IOException(
					"File " + file.getAbsolutePath() + " is not a file!");
		}

		return readSavedState(new SavedStateInfo(0/* unused */, file, null));
	}

	/**
	 * Reads a SignedState from disk
	 *
	 * @param info
	 * 		information about where the saved state is stored
	 * @return a pair of Hash and SignedState
	 * @throws IOException
	 * 		if there is any problems with reading from a file
	 */
	public static Pair<Hash, SignedState> readSavedState(final SavedStateInfo info)
			throws IOException {

		Pair<Hash, SignedState> returnState;

		final Triple<State, Hash, SigSet> data = deserializeAndDebugOnFailure(
				() -> new BufferedInputStream(new FileInputStream(info.getStateFile())),
				info.getDir(),
				(final MerkleDataInputStream in) -> {
					byte versionByte = in.readByte();
					if (versionByte != VERSIONED_FILE_BYTE) {
						throw new IOException(
								"File is not versioned -- data corrupted or is an unsupported legacy state");
					}

					in.readInt();// file version
					in.readProtocolVersion();

					final State state = in.readMerkleTree(MAX_MERKLE_NODES_IN_STATE);
					final Hash hash = in.readSerializable();
					final SigSet sigSet = in.readSerializable(true, () ->
							new SigSet(state.getPlatformState().getAddressBook()));

					return Triple.of(state, hash, sigSet);
				},
				() -> {
					log.error(EXCEPTION.getMarker(), "failed to load state");
					exitSystem(SAVED_STATE_NOT_LOADED);
				}
		);

		final SignedState newSignedState = new SignedState(data.getLeft());

		newSignedState.setSigSet(data.getRight());

		returnState = Pair.of(data.getMiddle(), newSignedState);

		if (!info.hasEvents()) {
			log.warn(LogMarker.ERROR.getMarker(),
					"No local data found in '{}'",
					info.getDir().getAbsolutePath());
			return returnState;
		}

		final LocalStateEvents localStateEvents = deserializeAndDebugOnFailure(
				() -> new BufferedInputStream(new FileInputStream(info.getEvents())),
				(final MerkleDataInputStream in) -> {
					in.readInt();// file version
					in.readProtocolVersion();
					return in.readSerializable();
				},
				() -> {
					log.error(EXCEPTION.getMarker(), "failed to load events");
					exitSystem(SAVED_STATE_NOT_LOADED);
				});

		returnState.getValue().setLocalStateEvents(localStateEvents);

		return returnState;
	}

	static void deleteRecursively(final File f) {
		log.info(STATE_TO_DISK.getMarker(), "deleting directory {}", f.getAbsolutePath());
		final boolean success = CommonUtils.deleteDirectory(f);
		if (success) {
			log.info(STATE_TO_DISK.getMarker(), "successfully deleted directory {}", f.getAbsolutePath());
		} else {
			log.error(EXCEPTION.getMarker(), "failed to delete directory {}", f.getAbsolutePath());
		}
	}

	/**
	 * Notifies the platform that the signed state is complete, the platform will then write it to a file
	 *
	 * @param signedState
	 * 		the complete signed state
	 * @return true if it will be written to disk, false otherwise
	 */
	public boolean saveSignedStateToDisk(SignedState signedState) {
		String taskDesc = "Signed state for round " + signedState.getLastRoundReceived();

		return offerTask(new FileManagerTask(
				FileManagerOperation.WRITE,
				signedState,
				signedState.getLastRoundReceived(),
				taskDesc,
				getSignedStateDir(signedState.getLastRoundReceived())));
	}

	/**
	 * Saves a signed state to disk that had an ISS.
	 *
	 * Ideally this would not be a separate method, but there wasn't a trivial way to change the directory of the
	 * snapshot, so the snapshot is not included in the ISS.
	 *
	 * @param signedState
	 * 		the signed state to be saved which has an ISS
	 */
	public void saveIssStateToDisk(final SignedState signedState) {
		offerTask(
				new FileManagerTask(
						FileManagerOperation.WRITE,
						signedState,
						signedState.getLastRoundReceived(),
						null,
						CommonUtils.canonicalFile(
								Settings.savedDirPath,
								"iss",
								String.format(
										"node%d_round%d",
										platform.getSelfId().getId(),
										signedState.getLastRoundReceived()
								)
						)
				)
		);
	}

	/**
	 * Saves a signed state to disk in response to a fatal exception. Blocks until completed.
	 *
	 * @param signedState
	 * 		the state to save
	 */
	public void saveFatalStateToDisk(final SignedState signedState) throws InterruptedException {
		log.info(STATE_TO_DISK.getMarker(), "writing state to disk in response to a fatal exception");

		final String taskDesc = "Fatal signed state for round " + signedState.getLastRoundReceived();

		final FileManagerTask task = new FileManagerTask(
				FileManagerOperation.WRITE,
				signedState,
				signedState.getLastRoundReceived(),
				taskDesc,
				CommonUtils.canonicalFile(
						Settings.savedDirPath,
						"fatal",
						String.format(
								"node%d_round%d",
								platform.getSelfId().getId(),
								signedState.getLastRoundReceived()
						)
				)
		);

		if (offerTask(task)) {
			task.waitUntilFinished();
		}
	}

	/**
	 * Tells the platform to delete the signed state from disk
	 *
	 * @param roundNumber
	 * 		the signed state to be deleted
	 */
	public void deleteSignedStateFromDisk(final Long roundNumber) {
		offerTask(
				new FileManagerTask(
						FileManagerOperation.DELETE,
						null,
						roundNumber,
						"delete task",
						getSignedStateDir(roundNumber)
				));
	}

	/**
	 * Add a task to the queue and log an error if it fails
	 *
	 * @param task
	 * 		the task to add to the queue
	 * @return true if the task was added
	 */
	private boolean offerTask(FileManagerTask task) {
		if (!taskQueue.offer(task)) {
			log.error(EXCEPTION.getMarker(),
					"SignedStateFileManager task: '{}' for round {} cannot be added because queue is full!",
					task.getDescription(), task.getRound());
			task.signedState.weakReleaseState();
			return false;
		}
		return true;
	}

	/**
	 * The same as {@link #getSavedStateFile(File, SignedStateFileType, boolean)} with the tmpFile value set to false
	 *
	 * @see #getSavedStateFile(File, SignedStateFileType, boolean)
	 */
	static File getSavedStateFile(File dir, SignedStateFileType type) {
		return getSavedStateFile(dir, type, false);
	}

	/**
	 * Get the File for a signed state. The file returned will be dependent on the file type. A signed state can be
	 * split into multiple files so the type will determine which part of the state the file is needed for.
	 *
	 * @param dir
	 * 		the directory in which is should ce contained
	 * @param type
	 * 		the type of file that should be returned
	 * @param tmpFile
	 * 		whether this is a temporary file or not
	 * @return the File object
	 */
	static File getSavedStateFile(File dir, SignedStateFileType type, boolean tmpFile) {
		String filename = null;
		switch (type) {
			case WHOLE_STATE:
				filename = "SignedState";
				break;
			case LOCAL:
				filename = "LocalData";
				break;
			case EVENTS:
				filename = "LocalEvents";
				break;
		}

		String extension = "";
		switch (type) {
			case WHOLE_STATE:
			case LOCAL:
				extension = Settings.swirldsFileExtension;
				break;
			case EVENTS:
				extension = ".evn";
				break;
		}
		if (tmpFile) {
			extension += ".tmp";
		}
		return CommonUtils.canonicalFile(dir, filename + extension);
	}


	/**
	 * Get the directory for a particular signed state. This directory might not exist
	 *
	 * @param round
	 * 		the round number for the signed state
	 * @return the File that represents the directory of the signed state for the particular round
	 */
	public File getSignedStateDir(long round) {
		return getSignedStateDir(round, platform.getMainClassName(), platform.getSelfId(), platform.getSwirldName());
	}

	static File getSignedStateDir(long round, String mainClassName, NodeId selfId, String swirldName) {
		return CommonUtils.canonicalFile(Settings.savedDirPath, mainClassName,
				selfId.toString(), swirldName, Long.toString(round));
	}

	/**
	 * Looks for saved state files locally and returns an array of them sorted from newest to oldest
	 *
	 * @param mainClassName
	 * 		the name of the main app class
	 * @param platformId
	 * 		the ID of the plaform
	 * @param swirldName
	 * 		the swirld name
	 * @return Information about saved states on disk, or null if none are found
	 */
	static SavedStateInfo[] getSavedStateFiles(String mainClassName, NodeId platformId,
			String swirldName) {
		File dir = CommonUtils.canonicalFile(Settings.savedDirPath, mainClassName,
				platformId.toString(), swirldName);
		if (!dir.exists() || !dir.isDirectory()) {
			return null;
		}
		File[] dirs = dir.listFiles(File::isDirectory);
		TreeMap<Long, SavedStateInfo> savedStates = new TreeMap<>();
		for (File subDir : dirs) {
			try {
				long round = Long.parseLong(subDir.getName());
				File stateFile = getSavedStateFile(subDir, SignedStateFileType.WHOLE_STATE);
				if (!stateFile.exists()) {
					log.warn(LogMarker.ERROR.getMarker(),
							"Saved state file ({}) not found, but directory exists '{}'",
							stateFile.getName(), subDir.getAbsolutePath());
					continue;
				}

				File events = getSavedStateFile(subDir, SignedStateFileType.EVENTS);
				savedStates.put(round, new SavedStateInfo(
						round,
						stateFile,
						events.exists() ? events : null
				));
			} catch (NumberFormatException e) {
				log.warn(LogMarker.ERROR.getMarker(),
						"Unexpected directory '{}' in '{}'",
						subDir.getName(), dir.getAbsolutePath());
			}

		}
		return savedStates.descendingMap().values().toArray(new SavedStateInfo[] { });
	}

	/** an internal class used to keep all details of the operation that needs to be performed */
	private class FileManagerTask {
		/** the operation that needs to be performed */
		private final FileManagerOperation operation;
		/** the object that needs to be serialized to a file, if the it's a write operation */
		private final SignedState signedState;
		/** the round number of the signed state */
		private final long round;
		/** an optional database snapshot task to be executed in tandem with the object file operation */
		/** a description of the task */
		private final String description;
		/** the directory where the files are stored */
		private final File dir;

		/**
		 * This latch is counted down when the task has been completed.
		 */
		private final CountDownLatch latch = new CountDownLatch(1);

		/**
		 * Block until the task has been completed.
		 *
		 * @throws InterruptedException
		 * 		if this thread is interrupted
		 */
		public void waitUntilFinished() throws InterruptedException {
			latch.await();
		}

		public FileManagerTask(FileManagerOperation operation, SignedState signedState, long round,
				String description, File dir) {
			this.operation = operation;
			this.signedState = signedState;
			this.round = round;
			this.description = description;
			this.dir = dir;
		}

		public FileManagerOperation getOperation() {
			return operation;
		}

		public SignedState getSignedState() {
			return signedState;
		}

		public long getRound() {
			return round;
		}

		public String getDescription() {
			return description;
		}

		public File getDir() {
			return dir;
		}

		public void finish() {
			latch.countDown();
		}
	}

	@FunctionalInterface
	public interface WritingConsumer<T> {
		void write(T t) throws IOException;
	}

	/** operations that can be performed */
	private enum FileManagerOperation {
		WRITE, DELETE
	}

	private enum SignedStateFileType {
		WHOLE_STATE,
		LOCAL,
		EVENTS
	}
}

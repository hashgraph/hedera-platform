/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swirlds.blob.internal.db.SnapshotManager;
import com.swirlds.blob.internal.db.SnapshotTask;
import com.swirlds.blob.internal.db.SnapshotTaskType;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.NodeId;
import com.swirlds.common.SwirldState;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.logging.LogMarker;
import com.swirlds.common.merkle.io.MerkleDataInputStream;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.platform.state.SavedStateInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.StateDumpSource;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;


public class SignedStateFileManager implements Runnable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** The signed state file was not versioned before, this byte was introduced to mark a versioned file */
	private static final byte VERSIONED_FILE_BYTE = Byte.MAX_VALUE;

	/** The current version of the signed state file */
	private static final int FILE_VERSION = 1;

	private static final int MAX_MERKLE_NODES_IN_SIGNED_STATE = Integer.MAX_VALUE;

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
			try {
				FileManagerTask task = taskQueue.take();

				switch (task.operation) {
					case WRITE:
						writeSignedStateToDisk(task.signedState, task.snapshotTask, task.description);
						break;
					case DELETE:
						deleteRecursively(task.round);
						break;
					default:
						log.error(EXCEPTION.getMarker(),
								"Error in SignedStateFileManager.run(), unknown task: {}",
								task.operation.toString());
						break;

				}
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception in SignedStateFileManager.run():", e);
			}
		}

	}

	/**
	 * Writes a SignedState to a file
	 *
	 * @param signedState
	 * 		the object to be written. Will have already been reserved for archival.
	 * @param snapshotTask
	 * 		an optional database snapshot operation to be performed when the object is written to disk
	 * @param taskDescription
	 * 		a description of the task
	 * @throws IOException
	 * 		if there is any problems with writing to a file
	 */
	void writeSignedStateToDisk(final SignedState signedState, final SnapshotTask snapshotTask,
			final String taskDescription) throws IOException {

		try {
			log.info(STATE_TO_DISK.getMarker(), "Started writing '{}' to disk", taskDescription);

			// the directory where the state will be stored
			File dir = this.getSignedStateDir(signedState.getLastRoundReceived());

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

			// we should prepare the snapshot if requested
			if (snapshotTask != null) {
				SnapshotManager.prepareSnapshot(snapshotTask);
			}

			try {
				writeAndRename(stateFile, tmpStateFile, (out) -> {
					out.write(VERSIONED_FILE_BYTE);
					out.writeInt(FILE_VERSION);
					out.writeProtocolVersion();
					out.writeMerkleTree(signedState);
					out.writeSerializable(signedState.getHash(), true);
					out.writeSerializable(signedState.getSigSet(), true);
				});

				log.info(STATE_TO_DISK.getMarker(),
						"Done writing saved state with HashEventsCons {}, starting with local events",
						() -> signedState.getHashEventsCons());

				writeAndRename(events, tmpEvents, (out) -> {
					out.writeInt(FILE_VERSION);
					out.writeProtocolVersion();
					out.writeSerializable(signedState.getLocalStateEvents(), true);
				});

				Settings.writeSettingsUsed(dir);

				// This was added to support writing signed state JSON files every time a binary signed state is written
				// This is enabled/disabled via settings and is disabled by default
				platform.getSignedStateManager().jsonifySignedState(signedState, StateDumpSource.DISK_WRITE);

			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception when writing the signed state for round {} to disk:",
						signedState.getLastRoundReceived(), e);
				return;
			}

			// if we are taking a database snapshot then we should wait on completion
			if (snapshotTask != null) {
				log.info(STATE_TO_DISK.getMarker(), "'{}' waiting for snapshotTask", taskDescription);
				snapshotTask.waitFor();
			}

			// Notify any registered listeners that we have written a signed state to disk
			NotificationFactory.getEngine().dispatch(
					StateWriteToDiskCompleteListener.class,
					new StateWriteToDiskCompleteNotification(
							signedState.getLastRoundReceived(),
							signedState.getConsensusTimestamp(),
							signedState.getState(),
							dir
					)
			);
		} finally {
			// release the signed state if it was reserved
			// set it to saved to disk in all cases so that it can be deleted from memory
			signedState.setSavedToDisk(true);
			signedState.weakReleaseState();
		}

		log.info(STATE_TO_DISK.getMarker(), "Finished writing '{}' to disk", taskDescription);
	}

	public static void writeAndRename(File file, File tmpFile,
			WritingConsumer<MerkleDataOutputStream> writeMethod) throws Exception {
		try (FileOutputStream fileOut = new FileOutputStream(tmpFile);
			 BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
			 MerkleDataOutputStream out = new MerkleDataOutputStream(bufOut, true)) {

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
	 * @param signedState
	 * 		the legacy signed state that will read the data
	 * @return a {@link Pair} containing the original {@link Hash} and the {@link SignedState} that were read from disk
	 * @throws IOException
	 * 		if there is any problems with reading from a file
	 */
	public static Pair<Hash, SignedState> readSignedStateFromFile(final File file, final SignedState signedState)
			throws IOException {
		if (!file.exists()) {
			throw new IOException(
					"File " + file.getAbsolutePath() + " does not exist!");
		}
		if (!file.isFile()) {
			throw new IOException(
					"File " + file.getAbsolutePath() + " is not a file!");
		}

		return readSavedState(
				new SavedStateInfo(0/* unused */, file, null),
				signedState
		);
	}

	/**
	 * Reads a SignedState from disk
	 *
	 * @param info
	 * 		information about where the saved state is stored
	 * @param signedState
	 * 		the signed state that will read the data
	 * @throws IOException
	 * 		if there is any problems with reading from a file
	 */
	public static Pair<Hash, SignedState> readSavedState(final SavedStateInfo info, final SignedState signedState)
			throws IOException {
		Pair<Hash, SignedState> returnState;
		try (FileInputStream fileIn = new FileInputStream(info.getStateFile());
			 BufferedInputStream bufIn = new BufferedInputStream(fileIn);
			 MerkleDataInputStream in = new MerkleDataInputStream(bufIn, true);) {
			// we need to read in the first byte to determine which kind of signed state file we are reading
			in.mark(1);
			byte firstByte = in.readByte();

			if (firstByte == VERSIONED_FILE_BYTE) {

				// Delete legacy state
				// If state on disk uses new merkle protocol this state is not needed
				// There will be no reservations, tryDelete should never fail
				if (signedState != null) {
					if (!signedState.tryDelete()) {
						log.error(LogMarker.ERROR.getMarker(), "Unable to delete legacy signed state.");
					}
				}

				in.readInt();// file version
				in.readProtocolVersion();

				final SignedState merkleState = in.readMerkleTree(MAX_MERKLE_NODES_IN_SIGNED_STATE);
				final Hash hash = in.readSerializable();
				final SigSet sigSet = in.readSerializable(true, () -> new SigSet(merkleState.getAddressBook()));

				merkleState.setSigSet(sigSet);

				returnState = Pair.of(hash, merkleState);
			} else {
				// unread the first byte in this case
				in.reset();
				signedState.copyFrom(in);
				signedState.copyFromExtra(in);

				returnState = Pair.of(signedState.getHash(), signedState);
			}
		}

		if (!info.hasEvents()) {
			log.warn(LogMarker.ERROR.getMarker(),
					"No local data found in '{}'",
					info.getDir().getAbsolutePath());
			return returnState;
		}

		try (FileInputStream fileIn = new FileInputStream(info.getEvents());
			 BufferedInputStream bufIn = new BufferedInputStream(fileIn);
			 MerkleDataInputStream in = new MerkleDataInputStream(bufIn, true);) {
			in.readInt();// file version
			in.readProtocolVersion();
			returnState.getValue().setLocalStateEvents(in.readSerializable());
		}

		return returnState;
	}

	void deleteRecursively(final long roundNumber) {
		deleteRecursively(getSignedStateDir(roundNumber));
	}

	static void deleteRecursively(final File f) {
		if (!f.exists()) {
			log.error(EXCEPTION.getMarker(),
					"Could not delete because it doesn't exist: '{}'", f.getAbsolutePath());
			return;
		}
		if (f.isDirectory()) {
			for (File c : f.listFiles())
				deleteRecursively(c);
		}
		boolean deleted = f.delete();
		if (!deleted) {
			log.error(EXCEPTION.getMarker(), "Could not delete: '{}'", f.getAbsolutePath());
		} else {
			log.info(STATE_TO_DISK.getMarker(), "Deleted: '{}'", f.getAbsolutePath());
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
		SnapshotTask snapshotTask = Settings.dbBackup.isActive()
				? new SnapshotTask(
				SnapshotTaskType.BACKUP,
				platform.getMainClassName(),
				platform.getSwirldName(),
				platform.getSelfId(),
				signedState.getLastRoundReceived())
				: null;

		boolean accepted = taskQueue.offer(new FileManagerTask(
				FileManagerOperation.WRITE,
				signedState,
				signedState.getLastRoundReceived(),
				snapshotTask,
				taskDesc));

		if (!accepted) {
			log.error(EXCEPTION.getMarker(),
					"'{}' cannot be written to disk because queue is full!",
					taskDesc);
		}

		return accepted;
	}

	/**
	 * Tells the platform to delete the signed state from disk
	 *
	 * @param roundNumber
	 * 		the signed state to be deleted
	 */
	public void deleteSignedStateFromDisk(final Long roundNumber) {
		//if (!deleteFileAsynchronously(getSignedStateDir(roundNumber))) {
		if (!taskQueue.offer(new FileManagerTask(FileManagerOperation.DELETE, null, roundNumber, null))) {
			log.error(EXCEPTION.getMarker(),
					"Signed state for round {} cannot be deleted from disk because queue is full!",
					roundNumber);
		}
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
		private final SnapshotTask snapshotTask;
		/** a description of the task */
		private final String description;

		private FileManagerTask(final FileManagerOperation operation,
				final SignedState signedState, final long round, final SnapshotTask snapshotTask) {
			this(operation, signedState, round, snapshotTask, null);
		}

		private FileManagerTask(final FileManagerOperation operation, final SignedState signedState,
				final long round, final SnapshotTask snapshotTask, final String description) {
			this.operation = operation;
			this.signedState = signedState;
			this.round = round;
			this.snapshotTask = snapshotTask;
			this.description = description;
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

		public SnapshotTask getSnapshotTask() {
			return snapshotTask;
		}

		public String getDescription() {
			return description;
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

	public static <T> void jsonify(final File file, final T node) throws IOException {
		final ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, true);
		mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
		mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		mapper.registerModule(new JavaTimeModule());

		if (file.exists()) {
			file.delete();
		}

		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		try (
				final FileOutputStream fos = new FileOutputStream(file);
				final BufferedOutputStream bos = new BufferedOutputStream(fos)
		) {
			mapper.writer()
					.withDefaultPrettyPrinter()
					.with(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
					.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
					.writeValue(bos, node);
		}
	}

	public void jsonify(final SignedState signedState, final boolean onDisk) throws IOException {
		final String fileName = (onDisk) ? "DiskLoadedSignedState.json" : "InMemorySignedState.json";
		final File file = new File(getSignedStateDir(signedState.getLastRoundReceived()), fileName);

		jsonify(file, signedState);
	}

	public void jsonify(final long round, final SwirldState swirldState, final boolean onDisk) throws IOException {
		final String fileName = (onDisk) ? "DiskLoadedSwirldState.json" : "InMemorySwirldState.json";
		final File file = new File(getSignedStateDir(round), fileName);

		jsonify(file, swirldState);
	}

	public void jsonify(final long round, final Event[] events, final boolean onDisk) throws IOException {
		final String fileName = (onDisk) ? "DiskLoadedHashgraph.json" : "InMemoryHashgraph.json";
		final File file = new File(getSignedStateDir(round), fileName);

		jsonify(file, events);
	}
}

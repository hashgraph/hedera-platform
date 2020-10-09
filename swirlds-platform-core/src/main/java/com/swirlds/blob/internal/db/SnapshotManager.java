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

package com.swirlds.blob.internal.db;

import com.swirlds.common.NodeId;
import com.swirlds.platform.internal.DatabaseSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.File;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.swirlds.blob.internal.Utilities.destroyQuietly;

public abstract class SnapshotManager {

	private static final String DATA_DIRECTORY = "dataDir";
	private static final String DATABASE_HOST = "dbConnection.host";
	private static final String DATABASE_PORT = "dbConnection.port";
	private static final String DATABASE_USERNAME = "dbConnection.userName";
	private static final String DATABASE_PASSWORD = "dbConnection.password";
	private static final String DATABASE_SCHEMA = "dbConnection.schema";
	private static final String SNAPSHOT_ID = "snapshot.id";
	private static final String STATE_APPLICATION = "state.application";
	private static final String STATE_WORLD = "state.world";
	private static final String STATE_NODE = "state.node";
	private static final String STATE_ROUND = "state.round";
	private static final String STATE_SAVED_DIR = "state.savedDir";

	private static final Logger log = LogManager.getLogger(SnapshotManager.class);
	private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	/** logs related to the database snapshot manager */
	private static final Marker LOGM_SNAPSHOT_MANAGER = MarkerManager.getMarker("SNAPSHOT_MANAGER");

	private static final BlockingQueue<SnapshotTask> taskQueue;
	private static final Thread thread;
	private static volatile boolean running;

	static {
		taskQueue = new LinkedBlockingQueue<>(10);
		thread = new Thread(SnapshotManager::worker);

		thread.setDaemon(true);
		thread.setName("< SnapshotManager >");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				running = false;
				thread.interrupt();
				thread.join();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));

		running = true;
		thread.start();
	}

	private SnapshotManager() {

	}

	static Thread getThread() {
		return thread;
	}

	static boolean isRunning() {
		return running;
	}

	static Queue<SnapshotTask> getTaskQueue() {
		return taskQueue;
	}

	/**
	 * Handle the snapshot task and add it to the taskQueue
	 *
	 * @param task
	 *        {@inheritDoc}
	 */
	public static void restoreSnapshot(final SnapshotTask task) {
		// Track time started while initiating the connection
		task.setTimeStarted(Instant.now());

		task.setSnapshotId("restore");

		final boolean accepted = taskQueue.offer(task);

		if (!accepted) {
			log.error(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Unable to process snapshot request {}", task);
		} else {
			log.debug(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Successfully queued snapshot request {}", task);
		}
	}

	public static void prepareSnapshot(final SnapshotTask task) {

		try {
			// Track time started while initiating the connection
			task.setTimeStarted(Instant.now());

			// Acquire the connection & track it
			final Connection conn = DbManager.acquire();
			conn.setAutoCommit(false);
			task.setConnection(conn);


			try (final CallableStatement stmt = conn.prepareCall("{ ? = call pg_export_snapshot() }")) {
				stmt.registerOutParameter(1, Types.VARCHAR);
				stmt.execute();

				final String snapshotId = stmt.getString(1);

				task.setSnapshotId(snapshotId);
			}

			final boolean accepted = taskQueue.offer(task);

			if (!accepted) {
				log.error(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Unable to process snapshot request {}", task);
			} else {
				log.debug(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Successfully queued snapshot request {}", task);
			}

		} catch (SQLException ex) {
			log.error(LOGM_EXCEPTION, "SnapshotManager: Failed to acquire a database connection", ex);
		}
	}

	private static void worker() {

		try {
			while (running) {

//				if (!taskQueue.isEmpty()) {
//					log.trace(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Current Queue Depth [size = {}]",
//							taskQueue.size());
//				}

				final SnapshotTask task = taskQueue.take();

				final Map<String, String> tokens = compileTokens(task);

				final SnapshotExecutable programExecutable = task.getExecutable();

				final String executable = replaceTokens(programExecutable.getProgram(), tokens);
				final String arguments = replaceTokens(programExecutable.getArguments(), tokens);

				final String command = String.format("%s %s", executable, arguments).trim();
				final List<String> commandLine = ArgumentTokenizer.tokenize(command);

				final ProcessBuilder pb = new ProcessBuilder(commandLine);

				try {
					final File cwd = new File(".").getCanonicalFile();

//					log.trace(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Current Working Directory [{}]", cwd);

					final Process process = pb.inheritIO().directory(cwd).start();
					int exitCode = process.waitFor();

					if (exitCode != 0) {
						task.setError(true);
						log.error(LOGM_EXCEPTION,
								"SnapshotManager: Process returned an exit code indicating a failure [{}]", exitCode);
					} else {
						task.setError(false);
						log.debug(LOGM_SNAPSHOT_MANAGER,
								"SnapshotManager: Process successfully executed for snapshot task {} ", task);
					}

				} catch (IOException ex) {
					log.error(LOGM_EXCEPTION, String.format("SnapshotManager: Failed to start process [%s]", command),
							ex);
				} finally {
					task.setTimeCompleted(Instant.now());

					destroyQuietly(task.getConnection());
					task.setConnection(null);

					// Unblock any threads waiting on this task
					task.setComplete(true);
					log.info(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Completed task {}", task);
				}

//				if (!taskQueue.isEmpty()) {
//					log.trace(LOGM_SNAPSHOT_MANAGER, "SnapshotManager: Current Queue Depth [size = {}]",
//							taskQueue.size());
//				}
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	private static String escape(final String text) {
		return new StringBuilder().append('"').append(text).append('"').toString();
	}

	private static Map<String, String> compileTokens(final SnapshotTask task) {
		final HashMap<String, String> replacements = new HashMap<>();
		final DatabaseSettings dbSettings = task.getDatabaseSettings();

		replacements.putIfAbsent(DATA_DIRECTORY, escape(task.getDataDir()));
		replacements.putIfAbsent(DATABASE_HOST, escape(dbSettings.getHost()));
		replacements.putIfAbsent(DATABASE_PORT, escape(Integer.toString(dbSettings.getPort())));
		replacements.putIfAbsent(DATABASE_USERNAME, escape(dbSettings.getUserName()));
		replacements.putIfAbsent(DATABASE_PASSWORD, escape(dbSettings.getPassword()));
		replacements.putIfAbsent(DATABASE_SCHEMA, escape(dbSettings.getSchema()));

		replacements.putIfAbsent(SNAPSHOT_ID, escape(task.getSnapshotId()));
		replacements.putIfAbsent(STATE_APPLICATION, escape(task.getApplicationName()));
		replacements.putIfAbsent(STATE_WORLD, escape(task.getWorldId()));
		replacements.putIfAbsent(STATE_NODE, escape(task.getNodeId().toString()));
		replacements.putIfAbsent(STATE_ROUND, escape(Long.toString(task.getRoundNumber())));
		replacements.putIfAbsent(STATE_SAVED_DIR, escape(task.getSavedDir().getAbsolutePath()));

		return replacements;
	}

	private static String replaceTokens(final String text, Map<String, String> replacements) {
		Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}");
		Matcher matcher = pattern.matcher(text);
		StringBuffer buffer = new StringBuffer();

		while (matcher.find()) {
			String replacement = replacements.get(matcher.group(1));
			if (replacement != null) {
				matcher.appendReplacement(buffer, "");
				buffer.append(replacement);
			}
		}

		matcher.appendTail(buffer);
		return buffer.toString();
	}

	/**
	 * Restore a snapshot for a directory containing a backup file.
	 *
	 * @param dataDir
	 * 		path to the data directory containing backup/pg_restore.sh
	 * @param roundDir
	 * 		path to the directory containing the backup file, formatted as:
	 * 		path/to/dir/applicationName/nodeId/worldId/roundNumber
	 * @throws IllegalArgumentException
	 *        {@inheritDoc}
	 */
	public static void restoreSnapshotFromFile(String dataDir, File roundDir) throws IllegalArgumentException {
		if (roundDir == null) {
			throw new IllegalArgumentException("snapshot file argument was null");
		}

		String applicationName;
		String worldId;
		NodeId nodeId;
		long roundNumber;

		try {
			roundNumber = Long.parseLong(roundDir.getName());

			File worldIdFolder = roundDir.getParentFile();
			worldId = worldIdFolder.getName();

			File nodeIdFolder = worldIdFolder.getParentFile();
			nodeId = new NodeId(false, Long.parseLong(nodeIdFolder.getName()));

			File applicationNameFolder = nodeIdFolder.getParentFile();
			applicationName = applicationNameFolder.getName();

			File savedDirPath = applicationNameFolder.getParentFile();

			SnapshotTask task = new SnapshotTask(SnapshotTaskType.RESTORE, dataDir, savedDirPath, applicationName,
					worldId, nodeId, roundNumber);

			SnapshotManager.restoreSnapshot(task);

			task.waitFor();
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"round directory path not formatted as expected, could not parse required fields", e);
		}
	}
}

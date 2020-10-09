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

import com.swirlds.platform.Marshal;
import com.swirlds.common.NodeId;
import com.swirlds.platform.internal.DatabaseBackupSettings;
import com.swirlds.platform.internal.DatabaseRestoreSettings;
import com.swirlds.platform.internal.DatabaseSettings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.time.Instant;

public class SnapshotTask {

	private SnapshotTaskType taskType;

	private DatabaseSettings databaseSettings;
	private SnapshotExecutable executable;
	private Connection connection;
	private String snapshotId;

	private String dataDir;
	private File savedDir;
	private String applicationName;
	private String worldId;
	private NodeId nodeId;
	private long roundNumber;

	private Instant timeStarted;
	private Instant timeCompleted;

	private boolean complete;
	private boolean error;

	/**
	 * Constructor for SnapshotTask
	 *
	 * @param taskType
	 *        either BACKUP or RESTORE
	 * @param applicationName
	 *        class name of the application
	 * @param worldId
	 *        world number
	 * @param nodeId
	 *        node for which the save was / will be generated
	 * @param roundNumber
	 *        round for state to save / recover from
	 */
	public SnapshotTask(final SnapshotTaskType taskType, final String applicationName, final String worldId, final NodeId nodeId, final long roundNumber) {
		this(
				taskType,
				Marshal.getDataDirPath().getAbsolutePath(),
				Marshal.getSavedDirPath(),
				applicationName,
				worldId,
				nodeId,
				roundNumber);
	}

	/**
	 * Constructor with modifiable dataDir and saveDir
	 *
	 * @param taskType
	 *        either BACKUP or RESTORE
	 * @param dataDir
	 *        path to folder containing backup/pg_(backup/restore).sh
	 * @param savedDir
	 *        path to folder where snapshot will be saved
	 * @param applicationName
	 *        class name of the application
	 * @param worldId
	 *        world number
	 * @param nodeId
	 *        node for which the save was / will be generated
	 * @param roundNumber
	 *        round for state to save / recover from
	 */
	public SnapshotTask(final SnapshotTaskType taskType, final String dataDir, final File savedDir, final String applicationName, final String worldId, final NodeId nodeId, final long roundNumber) {
		this.taskType = taskType;
		this.applicationName = applicationName;
		this.worldId = worldId;
		this.nodeId = nodeId;
		this.roundNumber = roundNumber;

		this.dataDir = dataDir;
		this.savedDir = savedDir;
		this.databaseSettings = Marshal.getDatabaseSettings();

		if (taskType == SnapshotTaskType.BACKUP) {
			final DatabaseBackupSettings settings = Marshal.getDatabaseBackupSettings();
			this.executable = new SnapshotExecutable(settings.getProgram(),settings.getArguments());
		} else {
			final DatabaseRestoreSettings settings = Marshal.getDatabaseRestoreSettings();
			this.executable = new SnapshotExecutable(settings.getProgram(), settings.getArguments());
		}
	}

	public SnapshotTaskType getTaskType() {
		return taskType;
	}

	DatabaseSettings getDatabaseSettings() {
		return databaseSettings;
	}

	SnapshotExecutable getExecutable() {
		return executable;
	}

	public Connection getConnection() {
		return connection;
	}

	void setConnection(final Connection connection) {
		this.connection = connection;
	}

	public String getSnapshotId() {
		return snapshotId;
	}

	void setSnapshotId(final String snapshotId) {
		this.snapshotId = snapshotId;
	}

	public String getDataDir() {
		return dataDir;
	}

	public File getSavedDir() {
		return savedDir;
	}

	public String getApplicationName() {
		return applicationName;
	}

	void setApplicationName(final String applicationName) {
		this.applicationName = applicationName;
	}

	public String getWorldId() {
		return worldId;
	}

	void setWorldId(final String worldId) {
		this.worldId = worldId;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	void setNodeId(final NodeId nodeId) {
		this.nodeId = nodeId;
	}

	public long getRoundNumber() {
		return roundNumber;
	}

	void setRoundNumber(final long roundNumber) {
		this.roundNumber = roundNumber;
	}

	public Instant getTimeStarted() {
		return timeStarted;
	}

	void setTimeStarted(final Instant timeStarted) {
		this.timeStarted = timeStarted;
	}

	public Instant getTimeCompleted() {
		return timeCompleted;
	}

	void setTimeCompleted(final Instant timeCompleted) {
		this.timeCompleted = timeCompleted;
	}

	public boolean isComplete() {
		return complete;
	}

	synchronized void setComplete(final boolean complete) {
		this.complete = complete;
		notifyAll();
	}

	public boolean isError() {
		return error;
	}

	void setError(final boolean error) {
		this.error = error;
	}

	public synchronized void waitFor() {
		try {
			while (!isComplete() && !isError()) {
				wait();
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public String toString() {
		return "[" +
				"taskType='" + taskType + '\'' +
				", applicationName='" + applicationName + '\'' +
				", worldId='" + worldId + '\'' +
				", nodeId=" + nodeId +
				", roundNumber=" + roundNumber +
				", snapshotId=" + snapshotId +
				", timeStarted=" + timeStarted +
				", timeCompleted=" + timeCompleted +
				", complete=" + complete +
				", error=" + error +
				" ]";
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (!(o instanceof SnapshotTask)) return false;

		final SnapshotTask that = (SnapshotTask) o;

		return new EqualsBuilder()
				.append(taskType,that.taskType)
				.append(nodeId, that.nodeId)
				.append(roundNumber, that.roundNumber)
				.append(snapshotId, that.snapshotId)
				.append(savedDir, that.savedDir)
				.append(applicationName, that.applicationName)
				.append(worldId, that.worldId)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(taskType)
				.append(nodeId)
				.append(roundNumber)
				.append(snapshotId)
				.append(savedDir)
				.append(applicationName)
				.append(worldId)
				.toHashCode();
	}
}

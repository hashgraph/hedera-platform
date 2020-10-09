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

package com.swirlds.platform.internal;

/**
 * A class that holds all settings related to database backups
 */
public class DatabaseRestoreSettings extends SubSetting {

	public boolean active = true;

	public String program = "/bin/bash";

	public String arguments = "${dataDir}/backup/pg_restore.sh ${dbConnection.host} ${dbConnection.port} " +
			"${dbConnection" +
			".userName} ${dbConnection.password} ${dbConnection.schema} ${snapshot.id} ${state.application} ${state" +
			".world} ${state.node} ${state.round} ${state.savedDir}";

	public boolean isActive() {
		return active;
	}

	public String getProgram() {
		return program;
	}

	public String getArguments() {
		return arguments;
	}
}

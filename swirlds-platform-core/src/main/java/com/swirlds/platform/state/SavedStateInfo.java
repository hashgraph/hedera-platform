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

package com.swirlds.platform.state;

import java.io.File;
import java.util.Objects;

public class SavedStateInfo {
	private final long round;
	private final File stateFile;
	private final File events;

	public SavedStateInfo(long round, File stateFile, File events) {
		Objects.requireNonNull(stateFile);
		this.round = round;
		this.stateFile = stateFile;
		this.events = events;
	}

	public long getRound() {
		return round;
	}

	public File getDir() {
		return stateFile.getParentFile();
	}

	public File getStateFile() {
		return stateFile;
	}

	public File getEvents() {
		return events;
	}

	public boolean hasEvents() {
		return events != null && events.exists() && events.isFile();
	}
}

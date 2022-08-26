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
		return stateFile.getAbsoluteFile().getParentFile();
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

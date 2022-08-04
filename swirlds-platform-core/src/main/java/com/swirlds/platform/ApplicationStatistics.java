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

package com.swirlds.platform;

import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.common.statistics.StatEntry;

import java.util.LinkedList;
import java.util.List;

/**
 * {@inheritDoc}
 *
 * Statistics for user applications.
 *
 * User need to call add entries first, then initialize the instance
 */
public class ApplicationStatistics extends AbstractStatistics {
	private List<StatEntry> tempList = new LinkedList<>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void updateOthers() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StatEntry[] getStatEntriesArray() {
		return statEntries;
	}

	/**
	 * Add new entry to statistics
	 *
	 * @param newEntry
	 * 		the new entry
	 */
	void addStatEntry(final StatEntry newEntry) {
		tempList.add(newEntry);
	}

	/**
	 * initialize all StatEntry added.
	 */
	void init() {
		statEntries = tempList.toArray(new StatEntry[0]);
		for (StatEntry stat : statEntries) {
			if (stat.init != null) {
				stat.buffered = stat.init.apply(Settings.halfLife);
			}
		}
		initStatEntries(Settings.showInternalStats);
		if (printStats) {
			printStats = false;
			printAvailableStats();
		}
	}
}

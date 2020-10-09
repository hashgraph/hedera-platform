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

import com.swirlds.common.internal.AbstractStatistics;
import com.swirlds.common.StatEntry;

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

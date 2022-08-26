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

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.common.utility.CommonUtils;

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
	private List<Metric> tempList = new LinkedList<>();

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
	public Metric[] getStatEntriesArray() {
		return statEntries;
	}

	/**
	 * Add new entry to statistics
	 *
	 * @param newEntry
	 * 		the new entry
	 * @deprecated use {@link ApplicationStatistics#addMetrics(Metric...)} instead
	 */
	@Deprecated(forRemoval = true)
	void addStatEntry(final Metric newEntry) {
		tempList.add(newEntry);
	}

	/**
	 * Add new entry to statistics
	 *
	 * @param metrics
	 * 		the new metrics
	 * @throws IllegalArgumentException if {@code metrics} is {@code null}
	 */
	void addMetrics(final Metric... metrics) {
		CommonUtils.throwArgNull(metrics, "metrics");
		tempList.addAll(List.of(metrics));
	}

	/**
	 * initialize all StatEntry added.
	 */
	@SuppressWarnings("removal")
	void init() {
		statEntries = tempList.toArray(new Metric[0]);
		for (Metric metric : statEntries) {
			metric.init();
		}
		initStatEntries(Settings.showInternalStats);
		if (printStats) {
			printStats = false;
			printAvailableStats();
		}
	}
}

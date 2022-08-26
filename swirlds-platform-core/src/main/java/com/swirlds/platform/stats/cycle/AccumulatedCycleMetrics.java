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

package com.swirlds.platform.stats.cycle;

import com.swirlds.common.metrics.Metric;
import com.swirlds.platform.stats.simple.AccumulatedAverageTime;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cycle metrics that are accumulated for each write period
 */
public class AccumulatedCycleMetrics implements CycleMetrics {
	/** The definition of a cycle with all of its intervals */
	private final CycleDefinition definition;
	/** The average time for a full cycle */
	private final AccumulatedAverageTime avgCycleTime;
	/** The fraction of time the thread spends executing the cycle */
	private final CycleBusyTime busyFraction;
	/** For each interval, the fraction of time (of the whole cycle time) spent on that interval */
	private final IntervalPercentageMetric[] intervalFraction;


	public AccumulatedCycleMetrics(final CycleDefinition definition) {
		this.definition = definition;
		this.avgCycleTime = new AccumulatedAverageTime(
				definition.getCategory(),
				definition.getName() + "-avgTime",
				"average time for a single cycle " + definition.getName()
		);
		this.busyFraction = new CycleBusyTime(
				definition.getCategory(),
				definition.getName() + "-busy",
				"the percentage of time the thread is busy "
		);
		this.intervalFraction = new IntervalPercentageMetric[definition.getNumIntervals()];

		for (int i = 0; i < definition.getNumIntervals(); i++) {
			intervalFraction[i] = new IntervalPercentageMetric(definition, i);
		}
	}

	@Override
	public void cycleFinished(final long[] intervalNanoTime) {
		final long totalTime = Arrays.stream(intervalNanoTime).sum();
		busyFraction.addBusyTime(totalTime);
		avgCycleTime.add(totalTime);

		for (int i = 0; i < definition.getNumIntervals(); i++) {
			intervalFraction[i].updateTime(totalTime, intervalNanoTime[i]);
		}
	}

	@Override
	public void idleTime(final long nanoTime) {
		busyFraction.addIdleTime(nanoTime);
	}

	@Override
	public int getNumIntervals() {
		return definition.getNumIntervals();
	}

	@Override
	public List<Metric> getAllEntries() {
		return Stream.concat(
						Stream.of(busyFraction, avgCycleTime),
						Arrays.stream(intervalFraction))
				.collect(Collectors.toList());
	}
}

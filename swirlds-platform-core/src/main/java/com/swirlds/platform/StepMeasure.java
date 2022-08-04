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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.swirlds.logging.LogMarker.EXCEPTION;

class StepMeasure {
	private static final Logger log = LogManager.getLogger();
	private final String measureName;
	@SuppressWarnings("unused")
	private volatile Checkpoint firstCheckpoint = null;
	private final Map<Long, Checkpoint> prevCheckpoints = new ConcurrentHashMap<>();
	private final Map<String, StepInfo> stepInfos = new ConcurrentHashMap<>();
	private final List<StepInfo> stepList = new LinkedList<>();
	@SuppressWarnings("unused")
	private volatile long count = 0;
	private boolean enabled = true;
	private final Instant createdTime = Instant.now();
	private static final int logSeconds = 500;
	private volatile long printCount = 0;
	private static final boolean printEveryTime = false;

	public StepMeasure(String measureName) {
		super();
		this.measureName = measureName;
	}

	public StepMeasure(String measureName, boolean enabled) {
		this(measureName);
		this.enabled = enabled;
	}

	public synchronized void addCheckpoint(String checkpointName) {
		if (!enabled) {
			return;
		}
		Checkpoint thisCheckpoint = new Checkpoint(checkpointName,
				Instant.now());
		Checkpoint prevCheckpoint = prevCheckpoints
				.get(Thread.currentThread().getId());
		if (prevCheckpoint != null) {
			String stepName = '"' + prevCheckpoint.checkpointName + "\" -> \""
					+ thisCheckpoint.getCheckpointName() + '"';
			StepInfo stepInfo = stepInfos.get(stepName);
			if (stepInfo == null) {
				stepInfo = new StepInfo(stepName);
				stepInfos.put(stepName, stepInfo);
				stepList.add(stepInfo);
			}
			long time = prevCheckpoint.getInstant()
					.until(thisCheckpoint.getInstant(), ChronoUnit.MILLIS);
			stepInfo.addTime(time);
		} else {
			firstCheckpoint = thisCheckpoint;
		}
		prevCheckpoints.put(Thread.currentThread().getId(), thisCheckpoint);
		count++;
	}

	public synchronized void lastCheckpoint(String checkpointName) {
		if (!enabled) {
			return;
		}
		addCheckpoint(checkpointName);
		prevCheckpoints.remove(Thread.currentThread().getId());
		if (printEveryTime || createdTime.until(Instant.now(),
				ChronoUnit.SECONDS) > (printCount + 1) * logSeconds) {
			printCount++;
			logStats();
		}
	}

	public void logStats() {
		StringBuilder sb = new StringBuilder();
		sb.append('\n');
		sb.append(measureName);
		sb.append('\n');
		for (StepInfo si : stepList) {
			si.appendStats(sb);
			sb.append('\n');
		}
		log.error(EXCEPTION.getMarker(), sb.toString());
	}

	private static class Checkpoint {
		private final String checkpointName;
		private final Instant instant;

		public Checkpoint(String checkpointName, Instant instant) {
			super();
			this.checkpointName = checkpointName;
			this.instant = instant;
		}

		public String getCheckpointName() {
			return checkpointName;
		}

		public Instant getInstant() {
			return instant;
		}

	}

	private static class StepInfo {
		private final String stepName;
		private volatile long sumTime = 0;
		private volatile long count = 0;
		private volatile long maxTime;
		private volatile long minTime;

		public StepInfo(String stepName) {
			super();
			this.stepName = stepName;
		}

		public synchronized void addTime(long time) {
			if (count == 0) {
				maxTime = time;
				minTime = time;
			} else {
				maxTime = Math.max(time, maxTime);
				minTime = Math.min(time, minTime);
			}
			count++;
			sumTime += time;
		}

		public void appendStats(StringBuilder sb) {
			sb.append("  ");
			sb.append("avgTime=");
			double avg = sumTime / ((double) count);
			sb.append(String.format("%8.3f", avg));
			sb.append(" | ");
			sb.append("maxTime=");
			sb.append(String.format("%5d", maxTime));
			sb.append(" | ");
			sb.append("minTime=");
			sb.append(String.format("%5d", minTime));
			sb.append(" | ");
			sb.append(stepName);
		}
	}
}

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
	private List<StepInfo> stepList = new LinkedList<>();
	@SuppressWarnings("unused")
	private volatile long count = 0;
	private boolean enabled = true;
	private final Instant createdTime = Instant.now();
	private int logSeconds = 500;
	private volatile long printCount = 0;
	private boolean printEveryTime = false;

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

	private class Checkpoint {
		private String checkpointName;
		private Instant instant;

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

	private class StepInfo {
		private final String stepName;
		private volatile long sumTime = 0;
		private volatile long count = 0;
		private volatile long maxTime;
		private volatile long minTime;

		public StepInfo(String stepName) {
			super();
			this.stepName = stepName;
		}

		public void addTime(long time) {
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

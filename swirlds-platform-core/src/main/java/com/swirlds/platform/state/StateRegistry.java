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

package com.swirlds.platform.state;

import com.swirlds.logging.payloads.ActiveStateThresholdPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import static com.swirlds.common.utility.CompareTo.isGreaterThan;
import static com.swirlds.common.utility.Units.SECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static java.lang.StrictMath.ceil;

/**
 * Keeps track of the {@link State} objects currently in memory.
 */
public final class StateRegistry {

	public static final Duration NO_MAX_DURATION = null;
	public static final int NO_MAX_COUNT = -1;

	private static final Logger LOG = LogManager.getLogger(StateRegistry.class);

	/**
	 * The number of states in memory should be proportional to the setting {@link StateSettings#signedStateKeep}.
	 * Multiply by this heuristic fudge factor to account for states that are in the process of being deleted.
	 */
	private static final double STATES_IN_MEMORY_MULTIPLICATIVE_FACTOR = 1.5;

	/**
	 * In addition to the states required by {@link StateSettings#signedStateKeep}, there will always be at least
	 * this many additional states in memory.
	 */
	private static final int STATES_IN_MEMORY_ADDITIVE_FACTOR = 2;

	/**
	 * The length of time that any state remains in memory should be proportional to
	 * {@link StateSettings#saveStatePeriod}. Multiply by this heuristic fudge factor to account
	 * for standard fluctuations in state lifecycle.
	 */
	private static final double STATE_AGE_MULTIPLICATIVE_FACTOR = 1.5;

	/**
	 * If {@link StateSettings#saveStatePeriod} is very short (or 0), add a short fudge factor to ensure that
	 * states in a healthy system do not trigger an error message in the log.
	 */
	private static final Duration STATE_AGE_ADDITIVE_FACTOR = Duration.ofSeconds(30);

	/**
	 * Contains records of states that are currently in memory or that were recently in memory.
	 */
	private static final List<StateRecord> records = new LinkedList<>();

	private static Instant previousRecordCreationTime;

	/**
	 * Configures the behavior of the state registry.
	 */
	private static StateSettings settings;

	/**
	 * The number of nodes running in the current process.
	 */
	private static int localNodeCount = 1;

	/**
	 * The last time an error was logged by this utility.
	 */
	private static Instant lastErrorLogTime = null;

	/**
	 * Called when the maximum number of active states is exceeded.
	 */
	private static Runnable maxStateCountExceededCallback;

	/**
	 * Called when the maximum age of active states is exceeded.
	 */
	private static Runnable maxStateAgeExceededCallback;


	private StateRegistry() {

	}

	/**
	 * Configure the StateRegistry with a settings object.
	 */
	public static synchronized void configureSettings(final StateSettings settings) {
		StateRegistry.settings = settings;
	}

	/**
	 * Inform the StateRegistry about the number of nodes running in the current process. Causes some thresholds
	 * to scale proportionate to the number of nodes (as there is only one StateRegistry per process).
	 *
	 * @param localNodeCount
	 * 		the number of nodes running in the current process
	 */
	public static synchronized void setLocalNodeCount(final int localNodeCount) {
		StateRegistry.localNodeCount = localNodeCount;
	}


	/**
	 * Provide a callback that is invoked if the maximum number of active states is exceeded.
	 */
	public static synchronized void setMaxStateCountExceededCallback(final Runnable maxStateCountExceededCallback) {
		StateRegistry.maxStateCountExceededCallback = maxStateCountExceededCallback;
	}

	/**
	 * Provide a callback that is invoked if an active state in memory exceeds the maximum age.
	 */
	public static synchronized void setMaxStateAgeExceededCallback(final Runnable maxStateAgeExceededCallback) {
		StateRegistry.maxStateAgeExceededCallback = maxStateAgeExceededCallback;
	}

	/**
	 * Get the maximum number of active states that are expected to sit in memory at any point in time. If the
	 * number of active states exceeds this threshold then an error may be logged.
	 *
	 * @return maximum expected state count
	 */
	public static int statesInMemoryThreshold() {
		if (settings == null) {
			return NO_MAX_COUNT;
		} else {
			final double statesInMemoryPerNode = settings.signedStateKeep * STATES_IN_MEMORY_MULTIPLICATIVE_FACTOR
					+ STATES_IN_MEMORY_ADDITIVE_FACTOR;
			return (int) ceil(statesInMemoryPerNode) * localNodeCount;
		}
	}

	/**
	 * Get the maximum expected age of any active state in memory (does not account for quiescent period).
	 * If the age of a state exceeds this value then an error may be logged.
	 *
	 * @return maximum expected age in seconds
	 */
	public static Duration stateAgeThreshold() {
		if (settings == null) {
			return NO_MAX_DURATION;
		} else {
			return Duration.ofMillis((long)
							(settings.saveStatePeriod * SECONDS_TO_MILLISECONDS * STATE_AGE_MULTIPLICATIVE_FACTOR))
					.plus(STATE_AGE_ADDITIVE_FACTOR);
		}
	}

	/**
	 * It is possible for the system to enter a state of quiescence where nothing really happens. In this
	 * condition, new states will not be created and old states will not be destroyed. This method estimates
	 * the maximum amount of time that the system may have been in a quiescent state since the oldest state
	 * record was registered.
	 */
	public static synchronized Duration quiescenceAdjustment() {
		Duration quiescentTime = Duration.ofSeconds(0);
		for (final StateRecord record : records) {
			quiescentTime = quiescentTime.plus(record.getTimeSincePreviousRecord());
		}
		return quiescentTime;
	}

	/**
	 * Create a new record for a state and add it to the records list.
	 *
	 * This method also is responsible for cleaning up old records and capturing statistics on existing
	 * records. By doing this work here we eliminate the need for a special thread to maintain this registry.
	 *
	 * @return a new {@link StateRecord}. Should be saved by the {@link State} object
	 * 		and released when the state is released.
	 */
	public static synchronized StateRecord createStateRecord() {
		final Instant now = Instant.now();
		final StateRecord record = new StateRecord(now);
		records.add(record);

		// Record time between creation of records. Used to factor quiescence time into the max age threshold.
		if (previousRecordCreationTime == null) {
			record.setTimeSincePreviousRecord(Duration.ofSeconds(0));
		} else {
			record.setTimeSincePreviousRecord(Duration.between(previousRecordCreationTime, now));
		}
		previousRecordCreationTime = now;

		clean();
		checkThresholds(now);

		return record;
	}

	/**
	 * Get the current number of active states in memory.
	 */
	public static synchronized int getActiveStateCount() {
		return records.size();
	}

	/**
	 * Get the age of the oldest active state in memory.
	 *
	 * @param now
	 * 		the current time
	 */
	public static synchronized Duration getOldestActiveStateAge(final Instant now) {
		if (records.isEmpty()) {
			return Duration.ofSeconds(0);
		}

		return Duration.between(records.get(0).getCreationTime(), now);
	}

	/**
	 * Remove all state records that have been released.
	 */
	public static synchronized void clean() {
		records.removeIf(StateRecord::isDestroyed);
	}

	/**
	 * Write an error message to the log if values exceed thresholds.
	 */
	public static synchronized void checkThresholds(final Instant now) {

		if (settings == null) {
			// If this object doesn't have its settings then return. Should not happen
			// in a running system, but likely to happen in test environments.
			return;
		}

		final int activeStates = getActiveStateCount();
		final Duration maximumAge = getOldestActiveStateAge(now);

		if (lastErrorLogTime != null && Duration.between(lastErrorLogTime, now).getSeconds()
				< settings.getStateDeletionErrorLogFrequencySeconds()) {
			// Don't spam the log with too many messages.
			return;
		}

		final int stateCountThreshold = statesInMemoryThreshold();
		if (activeStates > stateCountThreshold && stateCountThreshold != NO_MAX_COUNT) {
			LOG.warn(SIGNED_STATE.getMarker(), new ActiveStateThresholdPayload(
					"too many State objects in memory",
					ActiveStateThresholdPayload.ThresholdCategory.TOTAL_NUMBER_OF_STATES.name(),
					activeStates,
					statesInMemoryThreshold()));
			if (maxStateCountExceededCallback != null) {
				maxStateCountExceededCallback.run();
			}
			lastErrorLogTime = now;
		}

		final Duration ageThreshold = stateAgeThreshold();
		if (ageThreshold != NO_MAX_DURATION) {
			final Duration adjustedAgeThreshold = ageThreshold.plus(quiescenceAdjustment());
			if (isGreaterThan(maximumAge, adjustedAgeThreshold)) {
				LOG.warn(SIGNED_STATE.getMarker(), new ActiveStateThresholdPayload(
						"the age of the oldest State object is too high (units in seconds)",
						ActiveStateThresholdPayload.ThresholdCategory.TOTAL_NUMBER_OF_STATES.name(),
						maximumAge.toSeconds(),
						adjustedAgeThreshold.toSeconds()));
				if (maxStateAgeExceededCallback != null) {
					maxStateAgeExceededCallback.run();
				}
				lastErrorLogTime = now;
			}
		}
	}

	/**
	 * Drop all records of active states. Allows this class to be unit tested
	 * without interference from prior unit tests.
	 */
	public static synchronized void reset() {
		records.clear();
		lastErrorLogTime = null;
	}

}

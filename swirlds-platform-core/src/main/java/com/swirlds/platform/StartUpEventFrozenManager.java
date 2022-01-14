/*
 * (c) 2016-2022 Swirlds, Inc.
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

import com.swirlds.common.EventCreationRule;
import com.swirlds.common.EventCreationRuleResponse;
import com.swirlds.platform.components.TransThrottleSyncRule;
import com.swirlds.platform.stats.PlatformStatistics;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * This class is used for pausing event creation for a while, when the node starts from a saved state and {@link
 * Settings#freezeSecondsAfterStartup} is positive
 */
public class StartUpEventFrozenManager implements TransThrottleSyncRule, EventCreationRule {
	/** the time when this platforms startup event frozen ends */
	private volatile Instant startUpEventFrozenEndTime = null;
	/** a boolean that indicates whether the statistics have been reset after the startup freeze */
	private volatile boolean freezeResetStatistics = false;
	/** Stats supplier used to reset statistics */
	private final Supplier<PlatformStatistics> stats;
	/** Instant supplier used for unit testing */
	private final Supplier<Instant> now;

	StartUpEventFrozenManager(final Supplier<PlatformStatistics> stats,
			final Supplier<Instant> now) {
		this.stats = stats;
		this.now = now;
	}

	boolean isEventCreationPausedAfterStartUp() {
		// We first check if the startup event frozen is active, if it is null, it is not active.
		// This is to prevent nodes from creation
		// multiple events with the same sequence number. When a node does a restart or a reconnect, it must first
		// check if any other node has events that it created but does not have in memory at that moment.
		if (startUpEventFrozenEndTime != null) {
			if (!now.get().isAfter(startUpEventFrozenEndTime)) {
				// startup freeze has NOT passed
				return true;
			}
			if (!freezeResetStatistics) {
				// after the startup freeze, we need to reset the statistics
				freezeResetStatistics = true;
				if (stats != null && stats.get() != null) {
					stats.get().resetAllSpeedometers();
				}
			}
		}
		return false;
	}

	Instant getStartUpEventFrozenEndTime() {
		return startUpEventFrozenEndTime;
	}

	void setStartUpEventFrozenEndTime(Instant startUpEventFrozenEndTime) {
		this.startUpEventFrozenEndTime = startUpEventFrozenEndTime;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldSync() {
		// the node should sync during startup freeze
		return isEventCreationPausedAfterStartUp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventCreationRuleResponse shouldCreateEvent() {
		// the node should not create event during startup freeze
		if (isEventCreationPausedAfterStartUp()) {
			return EventCreationRuleResponse.DONT_CREATE;
		} else {
			return EventCreationRuleResponse.PASS;
		}
	}
}

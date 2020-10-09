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


import com.swirlds.common.NodeId;
import com.swirlds.platform.internal.FreezeSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.FREEZE;

/**
 * A class that handles all freeze related functionality
 */
public class FreezeManager {
	private static final Logger log = LogManager.getLogger();
	/** this boolean states whether events should be created or not */
	private volatile boolean freezeEventCreation = false;
	/** the time when this platforms startup freeze ends */
	private volatile Instant startupFreezeEnd = null;
	/** a boolean that indicates whether the statistics have been reset after the startup freeze */
	private volatile boolean freezeResetStatistics = false;
	/** indicates whether the freeze period has started */
	private volatile boolean freezePeriodStarted = false;

	/** reference to the platform that owns this FreezeManager */
	private final AbstractPlatform platform;
	/** ID of the platform that owns this FreezeManager */
	private final NodeId selfId;
	/** Stats supplier used to reset statistics */
	private final Supplier<Statistics> stats;
	/** Instant supplier used for unit testing */
	private final Supplier<Instant> now;
	/** A method to call when the freeze status changes */
	private final Runnable freezeChangeMethod;

	FreezeManager(final AbstractPlatform platform, final NodeId selfId, final Supplier<Statistics> stats,
			final Runnable freezeChangeMethod) {
		this(platform, selfId, stats, freezeChangeMethod, Instant::now);
	}

	FreezeManager(final AbstractPlatform platform, final NodeId selfId, final Supplier<Statistics> stats,
			final Runnable freezeChangeMethod,
			final Supplier<Instant> now) {
		this.platform = platform;
		this.selfId = selfId;
		this.stats = stats;
		this.freezeChangeMethod = freezeChangeMethod;
		this.now = now;
	}

	/**
	 * Checks whether the given instant is in the freeze period specified in the Settings
	 *
	 * @param instant
	 * 		the instant to check
	 * @return true if it is in the freeze period, false otherwise
	 */
	public static boolean isInFreezePeriod(Instant instant) {
		if (!Settings.freezeSettings.active) {
			// if all freezing is disabled, just return false
			return false;
		}
		LocalDateTime ldt = LocalDateTime.ofInstant(instant,
				ZoneOffset.UTC);
		int hour = ldt.getHour();
		int min = ldt.getMinute();

		if (Settings.freezeSettings.startHour > Settings.freezeSettings.endHour) {
			// the freeze starts in one day, but ends in another
			if (// it is after the beginning
					(hour > Settings.freezeSettings.startHour
							|| (hour == Settings.freezeSettings.startHour
							&& min >= Settings.freezeSettings.startMin))
							||// or is before the end in the next day
							(hour < Settings.freezeSettings.endHour
									|| (hour == Settings.freezeSettings.endHour
									&& min < Settings.freezeSettings.endMin))//
			) {//
				return true;
			} else {
				return false;
			}
		} else {
			// the start and end are in the same day
			if (// it is after the beginning
					(hour > Settings.freezeSettings.startHour
							|| (hour == Settings.freezeSettings.startHour
							&& min >= Settings.freezeSettings.startMin))
							&&// and is before the end
							(hour < Settings.freezeSettings.endHour
									|| (hour == Settings.freezeSettings.endHour
									&& min < Settings.freezeSettings.endMin))//
			) {//
				return true;
			} else {
				return false;
			}
		}
	}

	/**
	 * Returns whether events should be created or not
	 *
	 * @return true if we should create events, false otherwise
	 */
	boolean isEventCreationFrozen() {
		// We first check if the startup freeze is active, if it is null, it is not active.
		// Startup freeze needs to be active even if freeze is turned off. This is to prevent nodes from creation
		// multiple events with the same sequence number. When a node does a restart or a reconnect, it must first
		// check if any other node has events that it created but does not have in memory at that moment.
		if (startupFreezeEnd != null) {
			if (!now.get().isAfter(startupFreezeEnd)) {
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
			// continue to other checks
		}
		if (!Settings.freezeSettings.active) {
			// if all freezing is disabled, just return false
			return false;
		}

		// if not operating with beta mirror logic or if the node has stake then allow this check to exit early
		if (!Settings.enableBetaMirror || (platform != null && !platform.isZeroStakeNode())) {
			if (!freezeEventCreation) {
				// if it is false, no need to do more checks
				return false;
			}
		}

		if (FreezeManager.isInFreezePeriod(now.get())) {
			freezePeriodStarted = true;

			if (!freezeEventCreation && Settings.enableBetaMirror && platform != null && platform.isZeroStakeNode()) {
				setEventCreationFrozen(true);
			}
		} else {
			if (freezePeriodStarted) {
				// if the freeze period passed, unfreeze
				setEventCreationFrozen(false);
				freezePeriodStarted = false;
			}
		}
		return freezeEventCreation;
	}

	boolean shouldEnterMaintenance() {
		return freezeEventCreation;
	}

	Instant getStartupFreezeEnd() {
		return startupFreezeEnd;
	}

	void setStartupFreezeEnd(Instant startupFreezeEnd) {
		this.startupFreezeEnd = startupFreezeEnd;
	}

	/**
	 * Sets whether event creation is frozen
	 *
	 * @param freeze
	 * 		true=freeze, false=unfreeze
	 */
	void setEventCreationFrozen(boolean freeze) {
		freezeEventCreation = freeze;
		log.info(FREEZE.getMarker(), "Event creation {} in platform {}", freeze ? "frozen" : "unfrozen", selfId);
		freezeChangeMethod.run();
	}

	/**
	 * Notifies the freeze manager that a state has been loaded from disk
	 *
	 * @param consensusTimestamp
	 * 		the consensus timestamp of the state
	 */
	public void loadedStateFromDisk(Instant consensusTimestamp) {
		if (isInFreezePeriod(consensusTimestamp) && isInFreezePeriod(now.get())) {
			freezePeriodStarted = true;
			setEventCreationFrozen(true);
		}
	}

	public static void setFreezeTime(int startHour, int startMin, int endHour, int endMin) {


		if (startHour < 0 || startHour > 23) {
			throw new IllegalArgumentException("Invalid startHour: " + startHour);
		}
		if (endHour < 0 || endHour > 23) {
			throw new IllegalArgumentException("Invalid endHour: " + endHour);
		}
		if (startMin < 0 || startMin > 59) {
			throw new IllegalArgumentException("Invalid startMin: " + startMin);
		}
		if (endMin < 0 || endMin > 59) {
			throw new IllegalArgumentException("Invalid endMin: " + endMin);
		}

		FreezeSettings newSettings = new FreezeSettings(
				true, startHour, startMin, endHour, endMin);
		// This needs to be changed atomically
		Settings.freezeSettings = newSettings;
	}
}

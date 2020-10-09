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

package com.swirlds.platform.internal;

/**
 * A class that holds all settings related to daily event creation freezing
 */
public class FreezeSettings extends SubSetting {
	/**
	 * if this setting is false, the following 5 settings will be disregarded and the platform won't freeze
	 * at all
	 */
	public boolean active = false;
	/** the hour in which the freeze starts every day, used in combination with freezeTimeStartMin */
	public int startHour = 23;
	/**
	 * the minute in which the freeze starts every day, used in combination with freezeTimeStartHour. the
	 * freeze will start with the start of the minute
	 */
	public int startMin = 45;
	/** the hour in which the freeze ends every day, used in combination with freezeTimeEndMin */
	public int endHour = 23;
	/**
	 * the minute in which the freeze ends every day, used in combination with freezeTimeEndHour. the freeze
	 * will end when this minute starts
	 */
	public int endMin = 55;

	/**
	 * Default constructor with freeze set to not be active
	 */
	public FreezeSettings() {
	}

	/**
	 * A constructor with all settings
	 *
	 * @param active
	 * 		is freeze active
	 * @param startHour
	 * 		the hour in which the freeze starts every day
	 * @param startMin
	 * 		the minute in which the freeze starts every day
	 * @param endHour
	 * 		the hour in which the freeze ends every day
	 * @param endMin
	 * 		the minute in which the freeze ends every day
	 */
	public FreezeSettings(boolean active, int startHour, int startMin, int endHour, int endMin) {
		this.active = active;
		this.startHour = startHour;
		this.startMin = startMin;
		this.endHour = endHour;
		this.endMin = endMin;
	}

	@Override
	public String toString() {
		return "FreezeSettings{" +
				"active=" + active +
				", startHour=" + startHour +
				", startMin=" + startMin +
				", endHour=" + endHour +
				", endMin=" + endMin +
				'}';
	}
}

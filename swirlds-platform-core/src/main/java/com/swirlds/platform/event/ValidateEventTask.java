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

package com.swirlds.platform.event;

import com.swirlds.common.events.BaseEventHashedData;
import com.swirlds.common.events.BaseEventUnhashedData;
import com.swirlds.platform.EventStrings;

/**
 * A class used to hold information about an event from another node that requires validation.
 */
public class ValidateEventTask implements EventIntakeTask {

	private final BaseEventHashedData hashedData;
	private final BaseEventUnhashedData unhashedData;

	/**
	 * @param hashedData
	 * 		the hashed data for the event
	 * @param unhashedData
	 * 		the unhashed data for the event
	 */
	public ValidateEventTask(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
		this.hashedData = hashedData;
		this.unhashedData = unhashedData;
	}

	/**
	 * Get the hashed data for the event.
	 */
	public BaseEventHashedData getHashedData() {
		return hashedData;
	}

	/**
	 * Get the unhashed data for the event.
	 */
	public BaseEventUnhashedData getUnhashedData() {
		return unhashedData;
	}

	@Override
	public String toString() {
		return EventStrings.toMediumString(this);
	}
}

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.event;

import com.swirlds.platform.EventImpl;

/**
 * Used for storing the latest event created by me
 */
public interface SelfEventStorage {
	/**
	 * @return the most recent event created by me, or null if no such event exists.
	 */
	EventImpl getMostRecentSelfEvent();

	/**
	 * Sets the most recent self event to the supplied value
	 *
	 * @param selfEvent
	 * 		the value to set
	 */
	void setMostRecentSelfEvent(final EventImpl selfEvent);
}

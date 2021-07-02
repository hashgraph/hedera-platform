/*
 * (c) 2016-2021 Swirlds, Inc.
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

/**
 * An intake task requesting the creation of a new event.
 */
public class CreateEventTask implements EventIntakeTask {

	/**
	 * member whose event should be the other-parent (or -1 if none)
	 */
	private final long otherId;

	public CreateEventTask(final long otherId) {
		super();
		this.otherId = otherId;
	}

	/**
	 * Get the ID of the other-parent node of the event which this task represents
	 *
	 * @return the other-parent event/task ID
	 */
	public long getOtherId() {
		return otherId;
	}

	@Override
	public String toString() {
		return "(new)(otherId:" + otherId + ")objHash:" + hashCode();
	}
}

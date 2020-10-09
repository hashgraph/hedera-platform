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

package com.swirlds.platform.event;


import com.swirlds.platform.internal.CreatorSeqPair;

public class CreateEventTask extends EventIntakeTask {
	/** member whose event should be the other-parent (or -1 if none) */
	private long otherId;
	private boolean compensatingEvent;

	public CreateEventTask(final long otherId) {
		super();
		this.otherId = otherId;
	}

	public CreateEventTask(final long otherId, final boolean compensatingEvent) {
		this(otherId);
		this.compensatingEvent = compensatingEvent;
	}

	public long getOtherId() {
		return otherId;
	}

	public boolean isCompensatingEvent() {
		return compensatingEvent;
	}

	@Override
	public boolean isValidWait(final EventIntakeTask requester) {
		// wait for the event to be created
		getEventWait(requester);
		// an event that we create is always valid
		return true;
	}

	@Override
	public boolean isSelfEvent() {
		return true;
	}

	@Override
	public CreatorSeqPair getCreatorSeqPair() {
		throw new RuntimeException("Method not supported in CreateEventTask");
	}

	@Override
	public String toString() {
		return "(new)(otherId:" + otherId + ")objHash:" + hashCode();
	}
}

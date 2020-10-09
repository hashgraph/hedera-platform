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

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.internal.CreatorSeqPair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that represents a task for an event intake. This can be a an event that is about to be created by the
 * platform or it can be an event received from another node.
 */
public abstract class EventIntakeTask {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** the Event object, will be null until it is created */
	private volatile EventImpl event;
	/** a pointer the selfParent */
	private volatile EventIntakeTask selfParent;
	/** a pointer the otherParent */
	private volatile EventIntakeTask otherParent;
	/** indicates whether this event has any descendants */
	private volatile boolean childless;

	public EventIntakeTask() {
		childless = true;
	}

	/**
	 * Gets the validity of the event.
	 * <p>requester is no longer used due to the change to move validation to single threaded model and need not
	 *  wait to get validity of event. This will be re-evaluated in the future</p>
	 *
	 * @param requester
	 * 		the EventInfo that depends on this valid check
	 * @return true if the event is valid, false otherwise
	 */
	public abstract boolean isValidWait(EventIntakeTask requester);

	public abstract boolean isSelfEvent();

	public abstract CreatorSeqPair getCreatorSeqPair();

	/**
	 * Gets the instantiated event.
	 * <p>requester is no longer used due to the change to move validation to single threaded model and need not
	 * wait to get event. This will be re-evaluated in the future</p>
	 *
	 * @param requester
	 * 		the EventInfo that depends on this Event check
	 * @return the instantiated event, or null if it could not be instantiated
	 */
	public EventImpl getEventWait(EventIntakeTask requester) {
		return getEvent();
	}

	/**
	 * Gets the associated event, returning null if the event is not yet available or could not be instantiated
	 *
	 * @return the associated event, or null if unavailable
	 */
	public EventImpl getEvent() {
		return event;
	}

	/**
	 * Sets the event object and releases any threads waiting on getEventWait()
	 *
	 * @param event
	 * 		the event to associate with this EventInfo object
	 */
	public void setEvent(EventImpl event) {
		this.event = event;
	}

	public EventIntakeTask getSelfParent() {
		return selfParent;
	}

	public void setSelfParent(EventIntakeTask selfParent) {
		this.selfParent = selfParent;
		setChildlessFalse(selfParent);
	}

	public EventIntakeTask getOtherParent() {
		return otherParent;
	}

	public void setOtherParent(EventIntakeTask otherParent) {
		this.otherParent = otherParent;
		setChildlessFalse(otherParent);
	}

	public void clearParents() {
		selfParent = null;
		otherParent = null;
	}

	/**
	 * @return true if this event exists
	 */
	public boolean isChildless() {
		return childless;
	}

	/**
	 * An event always starts out childless, this method is called when a child is created for an event
	 */
	private void setChildlessFalse(EventIntakeTask eventInfo) {
		if (eventInfo != null) {
			eventInfo.childless = false;
		}
	}
}

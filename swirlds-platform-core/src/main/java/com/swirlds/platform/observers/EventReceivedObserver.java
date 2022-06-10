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

package com.swirlds.platform.observers;

import com.swirlds.platform.event.GossipEvent;

/**
 * An observer that is notified when an event is received, deduplicated and validated, but before it is added to
 * consensus. We might not have the parents of this event, so it might be added to consensus later, or never.
 */
@FunctionalInterface
public interface EventReceivedObserver {
	/**
	 * The given event has been received, deduplicated and validated
	 *
	 * @param event
	 * 		the event
	 */
	void receivedEvent(GossipEvent event);
}

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

package com.swirlds.platform.event.linking;

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;

/**
 * Responsible for linking {@link GossipEvent}s to their parents and creating an {@link EventImpl}
 */
public interface EventLinker {
	/**
	 * Submit an event that needs to be linked
	 *
	 * @param event
	 * 		the event that needs linking
	 */
	void linkEvent(GossipEvent event);

	/**
	 * Update the generations used to determine what is considered an ancient event
	 *
	 * @param generations
	 * 		the new generations
	 */
	void updateGenerations(GraphGenerations generations);

	/**
	 * @return true if there are any linked events available
	 */
	boolean hasLinkedEvents();

	/**
	 * Returns a previously submitted {@link GossipEvent} as a linked event. Should only be called if {@link
	 * #hasLinkedEvents()} returns true.
	 *
	 * @return a linked event
	 */
	EventImpl pollLinkedEvent();
}

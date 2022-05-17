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

package com.swirlds.platform.chatter.protocol.messages;

import com.swirlds.common.io.SelfSerializable;

import java.time.Instant;

/**
 * Describes an event, hiding interface details that are not relevant to a gossip algorithm.
 */
public interface ChatterEvent extends SelfSerializable {

	/**
	 * Get the descriptor of the event.
	 *
	 * @return the descriptor
	 */
	ChatterEventDescriptor getDescriptor();

	/**
	 * @return the time at which the event has been received
	 */
	Instant getTimeReceived();

	/**
	 * Get the generation of the event
	 *
	 * @return the generation of the event
	 */
	default long getGeneration() {
		return getDescriptor().getGeneration();
	}
}

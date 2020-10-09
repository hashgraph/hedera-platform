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

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.platform.EventImpl;

import java.time.Instant;

/**
 * An event that is empty and whose whole purpose is to unblock a queue that does not accept null
 */
@ConstructableIgnored
public class NoEvent extends EventImpl {
	private final Instant consensusTimestamp = Instant.now();

	public NoEvent() {
	}

	@Override
	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	@Override
	public String toString() {
		return "NoEvent object, consensusTimestamp: " + consensusTimestamp;
	}
}

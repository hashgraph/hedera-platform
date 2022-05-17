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

package com.swirlds.logging.payloads;

import java.time.Instant;

/**
 * This payload is logged when the platform sets last frozen time.
 */
public class SetLastFrozenTimePayload extends AbstractLogPayload {

	/** the last freezeTime based on which the nodes were frozen */
	private Instant lastFrozenTime;

	public SetLastFrozenTimePayload() {
	}

	public SetLastFrozenTimePayload(final Instant lastFrozenTime) {
		super("Set last frozen time");
		this.lastFrozenTime = lastFrozenTime;
	}

	public Instant getLastFrozenTime() {
		return lastFrozenTime;
	}

	public void setLastFrozenTime(Instant lastFrozenTime) {
		this.lastFrozenTime = lastFrozenTime;
	}
}

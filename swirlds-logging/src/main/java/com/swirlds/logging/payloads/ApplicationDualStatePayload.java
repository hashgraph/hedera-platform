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
 * This payload is logged when the swirld app receives a dual state instance
 */
public class ApplicationDualStatePayload extends AbstractLogPayload {

	/** the time when the freeze starts */
	private Instant freezeTime;

	/** the last freezeTime based on which the nodes were frozen */
	private Instant lastFrozenTime;

	public ApplicationDualStatePayload() {
	}

	public ApplicationDualStatePayload(final Instant freezeTime, final Instant lastFrozenTime) {
		super("App init with a dual state");
		this.freezeTime = freezeTime;
		this.lastFrozenTime = lastFrozenTime;
	}

	public Instant getFreezeTime() {
		return freezeTime;
	}

	public void setFreezeTime(Instant freezeTime) {
		this.freezeTime = freezeTime;
	}

	public Instant getLastFrozenTime() {
		return lastFrozenTime;
	}

	public void setLastFrozenTime(Instant lastFrozenTime) {
		this.lastFrozenTime = lastFrozenTime;
	}
}

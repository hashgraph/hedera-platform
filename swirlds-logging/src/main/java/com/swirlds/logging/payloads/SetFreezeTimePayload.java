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

package com.swirlds.logging.payloads;

import java.time.Instant;

/**
 * This payload is logged when the platform sets freeze time
 */
public class SetFreezeTimePayload extends AbstractLogPayload {

	/** the time when the freeze starts */
	private Instant freezeTime;

	public SetFreezeTimePayload() {
	}

	public SetFreezeTimePayload(final Instant freezeTime) {
		super("Set freeze time");
		this.freezeTime = freezeTime;
	}

	public Instant getFreezeTime() {
		return freezeTime;
	}

	public void setFreezeTime(Instant freezeTime) {
		this.freezeTime = freezeTime;
	}
}

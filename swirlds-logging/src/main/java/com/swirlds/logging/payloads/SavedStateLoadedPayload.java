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
 * This payload is logged when the platform loads a saved state from disk.
 */
public class SavedStateLoadedPayload extends AbstractLogPayload {

	private long round;
	private Instant consensusTimestamp;
	private Instant willFreezeUntil;

	public SavedStateLoadedPayload(final long round, final Instant consensusTimestamp,
			final Instant willFreezeUntil) {
		super("Platform has loaded a saved state");
		this.round = round;
		this.consensusTimestamp = consensusTimestamp;
		this.willFreezeUntil = willFreezeUntil;
	}

	public long getRound() {
		return round;
	}

	public void setRound(final long round) {
		this.round = round;
	}

	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public void setConsensusTimestamp(final Instant consensusTimestamp) {
		this.consensusTimestamp = consensusTimestamp;
	}

	public Instant getWillFreezeUntil() {
		return willFreezeUntil;
	}

	public void setWillFreezeUntil(final Instant willFreezeUntil) {
		this.willFreezeUntil = willFreezeUntil;
	}
}

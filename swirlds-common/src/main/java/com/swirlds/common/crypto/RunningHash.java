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

package com.swirlds.common.crypto;

import com.swirlds.common.AbstractHashable;
import com.swirlds.common.futures.WaitingFuture;

/**
 * Encapsulates a Hash object which denotes a running Hash calculated from all RunningHashable in history
 * up to this RunningHashable instance
 */
public class RunningHash extends AbstractHashable implements FutureHashable {
	private final WaitingFuture<Hash> futureHash;

	public RunningHash() {
		futureHash = new WaitingFuture<>();
	}

	public RunningHash(final Hash hash) {
		futureHash = new WaitingFuture<>(hash);
		super.setHash(hash);
	}

	@Override
	public WaitingFuture<Hash> getFutureHash() {
		return futureHash;
	}

	@Override
	public void setHash(final Hash hash) {
		futureHash.done(hash);
		super.setHash(hash);
	}
}

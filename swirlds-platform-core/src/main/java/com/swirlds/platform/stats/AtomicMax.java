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

package com.swirlds.platform.stats;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A maximum value that is updated atomically and is thread safe
 */
public class AtomicMax {
	/** default value to return if max is not initialized */
	private static final long DEFAULT_UNINITIALIZED = 0;

	private final AtomicLong max;
	/** the value to return before any values update the max */
	private final long uninitializedValue;

	public AtomicMax(final long uninitializedValue) {
		this.uninitializedValue = uninitializedValue;
		max = new AtomicLong(uninitializedValue);
	}

	public AtomicMax() {
		this(DEFAULT_UNINITIALIZED);
	}

	public long get() {
		return max.get();
	}

	public void reset(){
		max.set(uninitializedValue);
	}

	public long getAndReset(){
		return max.getAndSet(uninitializedValue);
	}

	public void update(final long value) {
		max.accumulateAndGet(value, Math::max);
	}
}

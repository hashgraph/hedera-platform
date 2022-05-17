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

package com.swirlds.common.io.extendable.extensions.internal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe implementation of the {@link Counter}
 */
public class ThreadSafeCounter implements Counter {

	/**
	 * the count of bytes passed through the stream
	 */
	private final AtomicLong count = new AtomicLong(0);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long addToCount(long value) {
		return count.addAndGet(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resetCount() {
		count.set(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCount() {
		return count.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getAndResetCount() {
		return count.getAndSet(0);
	}
}

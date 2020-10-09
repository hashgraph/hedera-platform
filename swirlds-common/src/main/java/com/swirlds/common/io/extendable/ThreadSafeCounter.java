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

package com.swirlds.common.io.extendable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe implementation of the {@link InternalCounter}
 */
public class ThreadSafeCounter implements InternalCounter {
	/** the count of bytes passed through the stream */
	private AtomicLong count = new AtomicLong(0);

	@Override
	public void addToCount(long value) {
		count.addAndGet(value);
	}

	@Override
	public void resetCount() {
		count.set(0);
	}

	@Override
	public long getCount() {
		return count.get();
	}

	@Override
	public long getAndResetCount() {
		return count.getAndSet(0);
	}
}

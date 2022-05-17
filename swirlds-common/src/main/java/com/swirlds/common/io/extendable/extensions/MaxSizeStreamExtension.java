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

package com.swirlds.common.io.extendable.extensions;

import com.swirlds.common.io.extendable.extensions.internal.Counter;
import com.swirlds.common.io.extendable.extensions.internal.StandardCounter;
import com.swirlds.common.io.extendable.extensions.internal.ThreadSafeCounter;

import java.io.IOException;

/**
 * This extension causes the stream to throw an exception if too many bytes pass through it.
 */
public class MaxSizeStreamExtension extends AbstractStreamExtension {

	private final long maxByteCount;
	private final Counter counter;

	/**
	 * Create a new thread safe max size extension.
	 *
	 * @param maxByteCount
	 * 		the maximum number of bytes that are allowed to pass through the stream
	 */
	public MaxSizeStreamExtension(final long maxByteCount) {
		this(maxByteCount, true);
	}

	/**
	 * Create an extension that limits the maximum number of bytes that pass through the stream.
	 *
	 * @param maxByteCount
	 * 		the maximum number of bytes that are tolerated
	 * @param threadSafe
	 * 		if true then this extension is safe to use with multiple streams, otherwise it should only
	 * 		be used by a single thread at a time
	 */
	public MaxSizeStreamExtension(final long maxByteCount, final boolean threadSafe) {
		this.maxByteCount = maxByteCount;
		if (threadSafe) {
			counter = new ThreadSafeCounter();
		} else {
			counter = new StandardCounter();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newByte(final int aByte) throws IOException {
		final long count = counter.addToCount(1);
		if (count > maxByteCount) {
			throw new IOException("number of bytes exceeds maximum of " + maxByteCount);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newBytes(final byte[] bytes, final int offset, final int length) throws IOException {
		final long count = counter.addToCount(length);
		if (count > maxByteCount) {
			throw new IOException("number of bytes exceeds maximum of " + maxByteCount);
		}
	}

	/**
	 * Reset the current count.
	 */
	public void reset() {
		counter.resetCount();
	}
}

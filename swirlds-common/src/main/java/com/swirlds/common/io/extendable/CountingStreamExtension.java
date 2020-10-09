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

/**
 * A stream extension that counts the number of bytes that pass through it
 */
public class CountingStreamExtension implements StreamExtension, Counter {
	private final InternalCounter counter;

	public CountingStreamExtension() {
		counter = new ThreadSafeCounter();
	}

	@Override
	public void newByte(int aByte) {
		counter.addToCount(1);
	}

	@Override
	public void newBytes(byte[] b, int off, int len) {
		counter.addToCount(len);
	}

	/**
	 * Resets the number of bytes read
	 */
	@Override
	public void resetCount() {
		counter.resetCount();
	}

	/**
	 * @return the number of bytes read by this stream since the last reset
	 */
	@Override
	public long getCount() {
		return counter.getCount();
	}

	/**
	 * Returns the number bytes read and resets the count to 0
	 *
	 * @return the number of bytes read since the last reset
	 */
	@Override
	public long getAndResetCount() {
		return counter.getAndResetCount();
	}
}

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

/**
 * A counter that is not thread safe.
 */
public class StandardCounter implements Counter {

	private long count;

	public StandardCounter() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void resetCount() {
		count = 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCount() {
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getAndResetCount() {
		final long ret = count;
		count = 0;
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long addToCount(final long value) {
		count += value;
		return count;
	}
}

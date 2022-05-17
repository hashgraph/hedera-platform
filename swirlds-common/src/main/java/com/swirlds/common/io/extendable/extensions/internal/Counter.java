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
 * An interface for a counter
 */
public interface Counter {

	/**
	 * Resets the count to 0
	 */
	void resetCount();

	/**
	 * get the current count
	 *
	 * @return the current count
	 */
	long getCount();

	/**
	 * Returns the current count and resets it to 0
	 *
	 * @return the count before the reset
	 */
	long getAndResetCount();

	/**
	 * Adds the specified value to the count
	 *
	 * @param value
	 * 		the value to be added
	 * @return the new count
	 */
	long addToCount(long value);
}

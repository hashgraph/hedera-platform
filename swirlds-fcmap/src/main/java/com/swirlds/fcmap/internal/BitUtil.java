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

package com.swirlds.fcmap.internal;

public final class BitUtil {

	/**
	 * Finds b = leftmost 1 bit in size (assuming size &gt; 1)
	 *
	 * @param value
	 * 		&gt; 1
	 * @return leftmost 1 bit
	 */
	public static long findLeftMostBit(final long value) {
		if (value == 0) {
			return 0;
		}

		long leftMostBit = 1L << 62;
		while ((value & leftMostBit) == 0) {
			leftMostBit >>= 1;
		}

		return leftMostBit;
	}
}

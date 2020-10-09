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

public interface StreamExtension {
	/**
	 * Notifies the stream that this byte has passed through it
	 *
	 * @param aByte
	 * 		the byte that has passed through the stream
	 */
	void newByte(int aByte);

	/**
	 * Notifies the stream that a number of bytes have passed through it
	 *
	 * @param b
	 * 		the byte buffer containing the data
	 * @param off
	 * 		the start offset in array b at which the data is
	 * @param len
	 * 		the number of bytes from off
	 */
	void newBytes(byte[] b, int off, int len);
}

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

package com.swirlds.common;

/**
 * Utility class for byte operations
 */
public abstract class ByteUtils {

	/**
	 * Return a long derived from the 8 bytes data[pos]...data[pos+7], big endian.
	 *
	 * @param data
	 * 		an array of bytes
	 * @param pos
	 * 		the first byte in the array to use
	 * @return the 8 bytes starting at pos, converted to a long, big endian
	 */
	public static long toLong(final byte[] data, final int pos) {
		//convert each byte to
		return ((data[pos + 0] & 0xffL) << (8 * 7))
				+ ((data[pos + 1] & 0xffL) << (8 * 6))
				+ ((data[pos + 2] & 0xffL) << (8 * 5))
				+ ((data[pos + 3] & 0xffL) << (8 * 4))
				+ ((data[pos + 4] & 0xffL) << (8 * 3))
				+ ((data[pos + 5] & 0xffL) << (8 * 2))
				+ ((data[pos + 6] & 0xffL) << (8 * 1))
				+ ((data[pos + 7] & 0xffL) << (8 * 0));
	}

	/**
	 * Write the long value into 8 bytes of the array, starting at position pos, big endian
	 *
	 * @param value
	 * 		the value to write
	 * @param data
	 * 		the array to write to
	 * @param pos
	 * 		write to 8 bytes starting with the byte with this index
	 */
	public static void toBytes(final long value, final byte[] data, final int pos) {
		data[pos + 0] = (byte) (value >> (8 * 7));
		data[pos + 1] = (byte) (value >> (8 * 6));
		data[pos + 2] = (byte) (value >> (8 * 5));
		data[pos + 3] = (byte) (value >> (8 * 4));
		data[pos + 4] = (byte) (value >> (8 * 3));
		data[pos + 5] = (byte) (value >> (8 * 2));
		data[pos + 6] = (byte) (value >> (8 * 1));
		data[pos + 7] = (byte) (value >> (8 * 0));
	}
}

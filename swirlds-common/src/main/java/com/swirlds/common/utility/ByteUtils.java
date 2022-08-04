/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.utility;

import static com.swirlds.common.utility.Units.BYTES_PER_INT;
import static com.swirlds.common.utility.Units.BYTES_PER_LONG;

/**
 * Utility class for byte operations
 */
public final class ByteUtils {

	private ByteUtils() {

	}

	private static final long LAST_BYTE_MASK_LONG = 0b1111_1111;
	private static final int LAST_BYTE_MASK_INT = (int) LAST_BYTE_MASK_LONG;

	/**
	 * Return a long derived from the 8 bytes data[position]...data[position+7], big endian.
	 *
	 * @param data
	 * 		an array of bytes
	 * @param position
	 * 		the first byte in the array to use
	 * @return the 8 bytes starting at position, converted to a long, big endian
	 */
	public static long byteArrayToLong(final byte[] data, final int position) {
		// Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
		return ((data[position] & LAST_BYTE_MASK_LONG) << (8 * 7))
				+ ((data[position + 1] & LAST_BYTE_MASK_LONG) << (8 * 6))
				+ ((data[position + 2] & LAST_BYTE_MASK_LONG) << (8 * 5))
				+ ((data[position + 3] & LAST_BYTE_MASK_LONG) << (8 * 4))
				+ ((data[position + 4] & LAST_BYTE_MASK_LONG) << (8 * 3))
				+ ((data[position + 5] & LAST_BYTE_MASK_LONG) << (8 * 2))
				+ ((data[position + 6] & LAST_BYTE_MASK_LONG) << (8))
				+ (data[position + 7] & LAST_BYTE_MASK_LONG);
	}

	/**
	 * Write the long value into 8 bytes of an array, big endian.
	 *
	 * @param value
	 * 		the value to write
	 * @return a byte array
	 */
	public static byte[] longToByteArray(final long value) {
		final byte[] data = new byte[BYTES_PER_LONG];
		longToByteArray(value, data, 0);
		return data;
	}

	/**
	 * Write the long value into 8 bytes of the array, starting at a given position pos, big endian.
	 *
	 * @param value
	 * 		the value to write
	 * @param data
	 * 		the array to write to
	 * @param position
	 * 		write to 8 bytes starting with the byte with this index
	 */
	public static void longToByteArray(final long value, final byte[] data, final int position) {
		// Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
		data[position] = (byte) (value >> (8 * 7));
		data[position + 1] = (byte) (value >> (8 * 6));
		data[position + 2] = (byte) (value >> (8 * 5));
		data[position + 3] = (byte) (value >> (8 * 4));
		data[position + 4] = (byte) (value >> (8 * 3));
		data[position + 5] = (byte) (value >> (8 * 2));
		data[position + 6] = (byte) (value >> (8));
		data[position + 7] = (byte) value;
	}

	/**
	 * Return an int derived from the 4 bytes data[position]...data[position+3], big endian.
	 *
	 * @param data
	 * 		an array of bytes
	 * @param position
	 * 		the first byte in the array to use
	 * @return the 4 bytes starting at position, converted to an int, big endian
	 */
	public static int byteArrayToInt(final byte[] data, final int position) {
		// Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
		return ((data[position] & LAST_BYTE_MASK_INT) << (8 * 3))
				+ ((data[position + 1] & LAST_BYTE_MASK_INT) << (8 * 2))
				+ ((data[position + 2] & LAST_BYTE_MASK_INT) << (8))
				+ (data[position + 3] & LAST_BYTE_MASK_INT);
	}

	/**
	 * Write the long value into 4 bytes of an array, big endian.
	 *
	 * @param value
	 * 		the value to write
	 */
	public static byte[] intToByteArray(final int value) {
		final byte[] data = new byte[BYTES_PER_INT];
		intToByteArray(value, data, 0);
		return data;
	}

	/**
	 * Write the long value into 4 bytes of the array, starting at a given position pos, big endian.
	 *
	 * @param value
	 * 		the value to write
	 * @param data
	 * 		the array to write to
	 * @param position
	 * 		write to 8 bytes starting with the byte with this index
	 */
	public static void intToByteArray(final int value, final byte[] data, final int position) {
		// Hard coded constants are used instead of a for loop to reduce the arithmetic required at runtime
		data[position] = (byte) (value >> (8 * 3));
		data[position + 1] = (byte) (value >> (8 * 2));
		data[position + 2] = (byte) (value >> (8));
		data[position + 3] = (byte) value;
	}
}

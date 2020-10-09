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

package com.swirlds.common.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class DataStreamUtils {

	/**
	 * write a byte array to the given stream
	 *
	 * @param stream
	 * 		the stream to write to
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static void writeByteArray(DataOutputStream stream, byte[] data)
			throws IOException {
		int len = (data == null ? 0 : data.length);
		stream.writeInt(len);
		for (int i = 0; i < len; i++) {
			stream.writeByte(data[i]);
		}
	}

	/**
	 * read a byte array from the given stream
	 *
	 * @param stream
	 * 		the stream to read from
	 * @return the array that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	@Deprecated
	public static byte[] readByteArray(DataInputStream stream)
			throws IOException {
		int len = stream.readInt();
		byte[] data = new byte[len];
		for (int i = 0; i < len; i++) {
			data[i] = stream.readByte();
		}
		return data;
	}

	/**
	 * read an int marker from the stream, and throw an exception if it isn't the expected value
	 *
	 * @param dis
	 * 		the stream to read from
	 * @param markerName
	 * 		the name of this marker (for logging)
	 * @param expectedValue
	 * 		what the value should be
	 * @return the value read
	 * @throws IOException
	 * 		if there are problems in reading from the stream
	 * @throws InvalidStreamPosition
	 * 		if the marker doesn't match what was read
	 */
	public static int readValidInt(final DataInputStream dis, final String markerName,
			final int expectedValue) throws IOException {
		final int value = dis.readInt();

		if (value != expectedValue) {
			throw new InvalidStreamPosition(markerName, expectedValue, value);
		}

		return value;
	}

	/**
	 * read a long marker from the stream, and throw an exception if it isn't the expected value
	 *
	 * @param dis
	 * 		the stream to read from
	 * @param markerName
	 * 		the name of this marker (for logging)
	 * @param expectedValue
	 * 		what the value should be
	 * @return the value read
	 * @throws IOException
	 * 		if there are problems in reading from the stream
	 * @throws InvalidStreamPosition
	 * 		the marker doesn't match what was read
	 */
	public static long readValidLong(final DataInputStream dis, final String markerName,
			final long expectedValue) throws IOException {
		final long value = dis.readLong();

		if (value != expectedValue) {
			throw new InvalidStreamPosition(markerName, expectedValue, value);
		}

		return value;
	}
}

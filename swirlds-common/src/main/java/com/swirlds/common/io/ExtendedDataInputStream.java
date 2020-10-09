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

import com.swirlds.common.CommonUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_INSTANT_EPOCH_SECOND;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;

/**
 * This data input stream provides additional functionality for deserializing various basic data structures.
 */
public class ExtendedDataInputStream extends DataInputStream {

	public ExtendedDataInputStream(InputStream in) {
		super(in);
	}

	/**
	 * Reads a byte array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @param readChecksum
	 * 		whether to read the checksum or not
	 * @return the byte[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public byte[] readByteArray(int maxLength, boolean readChecksum) throws IOException {
		int len = this.readInt();
		if (len < 0) {
			// if length is negative, it's a null value
			return null;
		}
		if (readChecksum) {
			int checksum = readInt();
			if (checksum != (101 - len)) { // must be at wrong place in the stream
				throw new BadIOException(
						"SerializableDataInputStream tried to create array of length "
								+ len + " with wrong checksum.");
			}
		}
		byte[] bytes;
		checkLengthLimit(len, maxLength);
		bytes = new byte[len];
		this.readFully(bytes);

		return bytes;
	}

	/**
	 * Same as {@link #readByteArray(int, boolean)} with {@code readChecksum} set to false
	 */
	public byte[] readByteArray(int maxLength) throws IOException {
		return readByteArray(maxLength, ExtendedDataOutputStream.debug);
	}

	/**
	 * Reads an int array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @return the int[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public int[] readIntArray(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		int[] data = new int[len];
		for (int i = 0; i < len; i++) {
			data[i] = readInt();
		}
		return data;
	}

	/**
	 * Reads an int list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<Integer> readIntList(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<Integer> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readInt());
		}
		return data;
	}

	/**
	 * Reads a long array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @return the long[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public long[] readLongArray(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		long[] data = new long[len];
		for (int i = 0; i < len; i++) {
			data[i] = readLong();
		}
		return data;
	}

	/**
	 * Reads an long list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<Long> readLongList(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<Long> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readLong());
		}
		return data;
	}


	/**
	 * Reads an boolean list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<Boolean> readBooleanList(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<Boolean> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readBoolean());
		}
		return data;
	}

	/**
	 * Reads a float array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @return the float[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public float[] readFloatArray(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		float[] data = new float[len];
		for (int i = 0; i < len; i++) {
			data[i] = readFloat();
		}
		return data;
	}

	/**
	 * Reads an float list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<Float> readFloatList(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<Float> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readFloat());
		}
		return data;
	}

	/**
	 * Reads a double array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @return the double[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public double[] readDoubleArray(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		double[] data = new double[len];
		for (int i = 0; i < len; i++) {
			data[i] = readDouble();
		}
		return data;
	}

	/**
	 * Reads an double list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<Double> readDoubleList(int maxLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<Double> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readDouble());
		}
		return data;
	}

	/**
	 * Reads a String array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @param maxStringLength
	 * 		The maximum expected length of a string in the array.
	 * @return the String[] read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public String[] readStringArray(int maxLength, int maxStringLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		String[] data = new String[len];
		for (int i = 0; i < len; i++) {
			data[i] = readNormalisedString(maxStringLength);
		}
		return data;
	}

	/**
	 * Reads an String list from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the list
	 * @param maxStringLength
	 * 		The maximum expected length of a string in the array.
	 * @return the list read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public List<String> readStringList(int maxLength, int maxStringLength) throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		List<String> data = new ArrayList<>(len);
		for (int i = 0; i < len; i++) {
			data.add(readNormalisedString(maxStringLength));
		}
		return data;
	}

	/**
	 * Reads an atomic long array from the stream.
	 *
	 * @param maxLength
	 * 		the maximum expected length of the array
	 * @return the AtomicLongArray read or null if null was written
	 * @throws IOException
	 * 		thrown if any problems occur
	 */
	public AtomicLongArray readAtomicLongArray(int maxLength)
			throws IOException {
		int len = readInt();
		if (len == NULL_LIST_ARRAY_LENGTH) {
			// if length is negative, it's a null value
			return null;
		}
		checkLengthLimit(len, maxLength);
		AtomicLongArray data = new AtomicLongArray(len);
		for (int i = 0; i < len; i++) {
			data.set(i, readLong());
		}
		return data;
	}

	/**
	 * Read an Instant from the stream
	 *
	 * @return the Instant that was read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	public Instant readInstant() throws IOException {
		long epochSecond = this.readLong(); // from getEpochSecond()
		if (epochSecond == NULL_INSTANT_EPOCH_SECOND) {
			return null;
		}

		long nanos = this.readLong();
		if (nanos < 0 || nanos > 999_999_999) {
			throw new IOException("Instant.nanosecond is not within the allowed range!");
		}

		return Instant.ofEpochSecond(epochSecond, nanos);
	}

	/**
	 * Reads a String encoded in the Swirlds default charset (UTF8) from the input stream
	 *
	 * @param maxLength
	 * 		the maximum length of the String in bytes
	 * @return the String read
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	public String readNormalisedString(int maxLength) throws IOException {
		byte[] data = readByteArray(maxLength);
		if (data == null) {
			return null;
		}
		return CommonUtils.getNormalisedStringFromBytes(data);
	}

	protected void checkLengthLimit(int length, int maxLength) throws IOException {
		if (length > maxLength) {
			throw new IOException(String.format(
					"The input stream provided a length of %d for the list/array " +
							"which exceeds the maxLength of %d",
					length, maxLength
			));
		}
	}
}

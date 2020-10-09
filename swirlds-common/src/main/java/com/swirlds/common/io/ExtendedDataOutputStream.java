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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

import static com.swirlds.common.io.SerializableStreamConstants.NULL_INSTANT_EPOCH_SECOND;
import static com.swirlds.common.io.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;

/**
 * This data output stream provides additional functionality for serializing various basic data structures.
 */
public class ExtendedDataOutputStream extends DataOutputStream {

	public ExtendedDataOutputStream(OutputStream out) {
		super(out);
	}

	/**
	 * Causes extra bytes/flags/checksums to be written/read to/from the stream. Useful for identifying
	 * the root cause of serialization bugs. Note: code with this flag activated cannot deserialize
	 * data serialized with the flag turned off (and vice versa).
	 */
	protected static final boolean debug = false;

	/**
	 * Writes a byte array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @param writeChecksum
	 * 		whether to read the checksum or not
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeByteArray(byte[] data, boolean writeChecksum) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
			return;
		}
		this.writeInt(data.length);
		if (writeChecksum) {
			// write a simple checksum to detect if at wrong place in the stream
			this.writeInt(101 - data.length);
		}
		this.write(data);
	}

	/**
	 * Same as {@link #writeByteArray(byte[], boolean)} with {@code writeChecksum} set to false
	 */
	public void writeByteArray(byte[] data) throws IOException {
		writeByteArray(data, debug);
	}

	/**
	 * Writes an int array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeIntArray(int[] data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.length);
			for (int datum : data) {
				writeInt(datum);
			}
		}
	}

	/**
	 * Writes an int list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeIntList(List<Integer> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (int datum : data) {
				writeInt(datum);
			}
		}
	}

	/**
	 * Writes a long array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeLongArray(long[] data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.length);
			for (long datum : data) {
				writeLong(datum);
			}
		}
	}

	/**
	 * Writes a long list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeLongList(List<Long> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (long datum : data) {
				writeLong(datum);
			}
		}
	}

	/**
	 * Writes a boolean list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeBooleanList(List<Boolean> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (boolean datum : data) {
				writeBoolean(datum);
			}
		}
	}


	/**
	 * Writes a float array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeFloatArray(float[] data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.length);
			for (float datum : data) {
				writeFloat(datum);
			}
		}
	}

	/**
	 * Writes a float list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeFloatList(List<Float> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (Float datum : data) {
				writeFloat(datum);
			}
		}
	}

	/**
	 * Writes a double array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeDoubleArray(double[] data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.length);
			for (double datum : data) {
				writeDouble(datum);
			}
		}
	}

	/**
	 * Writes a double list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeDoubleList(List<Double> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (Double datum : data) {
				writeDouble(datum);
			}
		}
	}

	/**
	 * Writes a String array to the stream. Can be null.
	 *
	 * @param data
	 * 		the array to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeStringArray(String[] data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.length);
			for (String datum : data) {
				writeNormalisedString(datum);
			}
		}
	}

	/**
	 * Writes a string list to the stream. Can be null.
	 *
	 * @param data
	 * 		the list to write
	 * @throws IOException
	 * 		thrown if any IO problems occur
	 */
	public void writeStringList(List<String> data) throws IOException {
		if (data == null) {
			this.writeInt(NULL_LIST_ARRAY_LENGTH);
		} else {
			this.writeInt(data.size());
			for (String datum : data) {
				writeNormalisedString(datum);
			}
		}
	}

	/**
	 * Normalizes the string in accordance with the Swirlds default normalization method (NFD) and writes it
	 * to the output stream encoded in the Swirlds default charset (UTF8). This is important for having a
	 * consistent method of converting Strings to bytes that will guarantee that two identical strings will
	 * have an identical byte representation
	 *
	 * @param s
	 * 		the String to be converted and written
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	public void writeNormalisedString(String s) throws IOException {
		byte[] data = CommonUtils.getNormalisedStringBytes(s);
		this.writeByteArray(data);
	}

	/**
	 * Write an Instant to the stream
	 *
	 * @param instant
	 * 		the instant to write
	 * @throws IOException
	 * 		thrown if there are any problems during the operation
	 */
	public void writeInstant(Instant instant) throws IOException {
		if (instant == null) {
			this.writeLong(NULL_INSTANT_EPOCH_SECOND);
			return;
		}
		this.writeLong(instant.getEpochSecond());
		this.writeLong(instant.getNano());
	}
}

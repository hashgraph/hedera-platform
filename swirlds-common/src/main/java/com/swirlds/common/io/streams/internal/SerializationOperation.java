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

package com.swirlds.common.io.streams.internal;

import com.swirlds.common.io.streams.AugmentedDataInputStream;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Describes a serialization operation.
 */
public enum SerializationOperation {

	/**
	 * Marks the opening of the stream.
	 */
	STREAM_OPENED,

	/**
	 * {@link InputStream#read()}
	 */
	READ,

	/**
	 * {@link InputStream#skip(long)}
	 */
	SKIP,

	/**
	 * {@link InputStream#readAllBytes()}
	 */
	READ_ALL_BYTES,

	/**
	 * {@link InputStream#readNBytes(int)}
	 */
	READ_N_BYTES,

	/**
	 * {@link InputStream#readNBytes(int)}
	 */
	READ_N_BYTES_ARRAY,

	/**
	 * {@link InputStream#skipNBytes(long)}
	 */
	SKIP_N_BYTES,

	/**
	 * {@link DataInputStream#readFully(byte[])}
	 */
	READ_FULLY,

	/**
	 * {@link DataInputStream#readFully(byte[], int, int)}
	 */
	READ_FULLY_OFFSET,

	/**
	 * {@link DataInputStream#skipBytes(int)}
	 */
	SKIP_BYTES,

	/**
	 * {@link DataInputStream#readBoolean()}
	 */
	READ_BOOLEAN,

	/**
	 * {@link DataInputStream#readByte()}
	 */
	READ_BYTE,

	/**
	 * {@link DataInputStream#readUnsignedByte()}
	 */
	READ_UNSIGNED_BYTE,

	/**
	 * {@link DataInputStream#readShort()}
	 */
	READ_SHORT,

	/**
	 * {@link DataInputStream#readUnsignedShort()}
	 */
	READ_UNSIGNED_SHORT,

	/**
	 * {@link DataInputStream#readChar()}
	 */
	READ_CHAR,

	/**
	 * {@link DataInputStream#readInt()}
	 */
	READ_INT,

	/**
	 * {@link DataInputStream#readLong()}
	 */
	READ_LONG,

	/**
	 * {@link DataInputStream#readFloat()}
	 */
	READ_FLOAT,

	/**
	 * {@link DataInputStream#readDouble()}
	 */
	READ_DOUBLE,

	/**
	 * {@link DataInputStream#readLine()}
	 */
	READ_LINE,

	/**
	 * {@link DataInputStream#readUTF()}
	 */
	READ_UTF,

	/**
	 * {@link AugmentedDataInputStream#readByteArray(int)} and
	 * {@link AugmentedDataInputStream#readByteArray(int, boolean)}
	 */
	READ_BYTE_ARRAY,

	/**
	 * {@link AugmentedDataInputStream#readIntList(int)} and
	 * {@link AugmentedDataInputStream#readIntArray(int)}
	 */
	READ_INT_LIST,

	/**
	 * {@link AugmentedDataInputStream#readLongList(int)} and
	 * {@link AugmentedDataInputStream#readLongArray(int)}
	 */
	READ_LONG_LIST,

	/**
	 * {@link AugmentedDataInputStream#readBooleanList(int)}
	 */
	READ_BOOLEAN_LIST,

	/**
	 * {@link AugmentedDataInputStream#readFloatList(int)} and
	 * {@link AugmentedDataInputStream#readFloatArray(int)}
	 */
	READ_FLOAT_LIST,

	/**
	 * {@link AugmentedDataInputStream#readDoubleList(int)} and
	 * {@link AugmentedDataInputStream#readDoubleArray(int)}
	 */
	READ_DOUBLE_LIST,

	/**
	 * {@link AugmentedDataInputStream#readStringList(int, int)} and
	 * {@link AugmentedDataInputStream#readStringArray(int, int)}
	 */
	READ_STRING_LIST,

	/**
	 * {@link AugmentedDataInputStream#readInstant()}
	 */
	READ_INSTANT,

	/**
	 * {@link AugmentedDataInputStream#readNormalisedString(int)}
	 */
	READ_NORMALISED_STRING,

	/**
	 * All variants of {@link SerializableDataInputStream#readSerializable()}
	 */
	READ_SERIALIZABLE,

	/**
	 * All variants of {@link SerializableDataInputStream#readSerializableList(int, boolean, Supplier)}
	 * and {@link SerializableDataInputStream#readSerializableArray(IntFunction, int, boolean, Supplier)}
	 */
	READ_SERIALIZABLE_LIST,

	/**
	 * {@link MerkleDataInputStream#readMerkleTree(int)}
	 */
	READ_MERKLE_TREE,

	/**
	 * Called every time {@link MerkleDataInputStream#readMerkleTree(int)} deserializes
	 * a merkle node.
	 */
	READ_MERKLE_NODE
}

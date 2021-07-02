/*
 * (c) 2016-2021 Swirlds, Inc.
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

public abstract class SerializableStreamConstants {
	/** The value of the length of an array/list will when the array/list is null */
	static final int NULL_LIST_ARRAY_LENGTH = -1;
	/** The class ID of a {@link SelfSerializable} instance when the instance is null */
	public static final long NULL_CLASS_ID = Long.MIN_VALUE;
	/** The version of a {@link SelfSerializable} instance when the instance is null */
	static final int NULL_VERSION = Integer.MIN_VALUE;
	/** The value of Instant.epochSecond when instant is null */
	static final long NULL_INSTANT_EPOCH_SECOND = Long.MIN_VALUE;

	/** number of bytes used by a boolean variable during serialization */
	public static final int BOOLEAN_BYTES = Byte.BYTES;
	/** number of bytes used by a class ID variable during serialization */
	public static final int CLASS_ID_BYTES = Long.BYTES;
	/** number of bytes used by a version variable during serialization */
	public static final int VERSION_BYTES = Integer.BYTES;

	/**
	 * The current version of the serialization protocol implemented by {@link SerializableDataOutputStream} and
	 * {@link SerializableDataInputStream}
	 */
	static final int SERIALIZATION_PROTOCOL_VERSION = 1;

	public static class MerkleSerializationProtocolVersion {

		/**
		 * The serialization protocol used for merkle trees.
		 */
		public static final int ORIGINAL = 1;
		public static final int ADDED_OPTIONS = 2;
		public static final int CURRENT = ADDED_OPTIONS;
	}
}

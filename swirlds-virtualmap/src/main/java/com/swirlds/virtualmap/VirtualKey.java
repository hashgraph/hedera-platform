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

package com.swirlds.virtualmap;

import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A virtual key, specifically for use with the Virtual FCMap {@code VirtualMap}. The indexes
 * used for looking up values are all stored on disk in order to support virtualization to
 * massive numbers of entities. This requires that any key used with the {@code VirtualMap}
 * needs to be serializable. To improve performance, this interface exposes some methods that
 * avoid instance creation and serialization for normal key activities like equality.
 * <p>
 * Keys must implement {@link Comparable}.
 */
public interface VirtualKey<T extends Comparable<? super T>> extends SelfSerializable, Comparable<T> {

	/**
	 * This needs to be a very good quality hash code with even spread, or it will be very inefficient when used in
	 * HalfDiskHashMap.
	 *
	 * @return Strong well distributed hash code
	 */
	@Override
	int hashCode();

	/**
	 * Serialize to a ByteBuffer. This serialization's data should match that of the stream serialization of
	 * SelfSerializable so the data can be written by one and read by the other. The reason for having the extra method
	 * here is that it is inefficient in a hot spot area to have to wrap a ByteBuffer into an input or output stream for
	 * every small read or write.
	 *
	 * Just like SelfSerializable we do not write our classes version here as that is handled by the calling class.
	 *
	 * @param buffer
	 * 		The buffer to serialize into, at the current position of that buffer. The buffers position should
	 * 		not be changed other than it being incremented by the amount of data written.
	 * @throws IOException
	 * 		If there was a problem writing this classes data into the ByteBuffer
	 */
	void serialize(ByteBuffer buffer) throws IOException;

	/**
	 * Deserialize from a ByteBuffer. This should read the data that was written by serialize(ByteBuffer buffer) and
	 * SelfSerializable so the data can be written by one and read by the other. The reason for having the extra method
	 * here is that it is inefficient in a hot spot area to have to wrap a ByteBuffer into an input or output stream for
	 * every small read or write.
	 *
	 * @param buffer
	 * 		The buffer to deserialize from, at the current position of that buffer. The buffers position should
	 * 		not be changed other than it being incremented by the amount of data read.
	 * @throws IOException
	 * 		If there was a problem reading this classes data from the ByteBuffer
	 */
	void deserialize(ByteBuffer buffer, int version) throws IOException;
}

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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.virtualmap.datasource.VirtualDataSource;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link VirtualValue} is a "virtual" value, and is part of the API for the {@code VirtualMap}.
 * {@code VirtualMap}s, by their nature, need both keys and values which are serializable
 * and {@link FastCopyable}. To enhance performance, serialization methods that work with
 * {@link ByteBuffer} are required on a VValue.
 */
public interface VirtualValue extends SelfSerializable, FastCopyable {

	@Override
	VirtualValue copy();

	/**
	 * Gets a copy of this Value which is entirely read-only.
	 *
	 * @return A non-null copy that is read-only. Can be a view rather than a copy.
	 */
	VirtualValue asReadOnly();

	/**
	 * Serialize this value into the specified buffer. The buffer will be pre-sized and
	 * prepared. The specific {@link VirtualDataSource}
	 * implementation you use will require information on the number of bytes per
	 * value so that it can prepare such a buffer ahead of time.
	 *
	 * @param buffer
	 * 		The buffer to fill. Will never be null.
	 * @throws IOException
	 * 		If an I/O exception happens during serialization.
	 */
	void serialize(final ByteBuffer buffer) throws IOException;

	void deserialize(final ByteBuffer buffer, final int version) throws IOException;
}

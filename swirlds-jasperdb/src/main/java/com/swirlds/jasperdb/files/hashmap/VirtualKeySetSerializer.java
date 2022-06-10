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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.swirlds.common.utility.Units.BYTES_PER_LONG;

/**
 * A key serializer used by {@link HalfDiskVirtualKeySet} when JPDB is operating in long key mode.
 * This key serializer only implements methods require to serialize a long key, and is not a general
 * purpose key serializer.
 */
@ConstructableIgnored
public class VirtualKeySetSerializer implements KeySerializer<VirtualKey<VirtualLongKey>> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSerializedSize() {
		return BYTES_PER_LONG;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public VirtualKey<VirtualLongKey> deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int serialize(
			final VirtualKey<VirtualLongKey> data,
			final SerializableDataOutputStream outputStream) throws IOException {
		outputStream.writeLong(((VirtualLongKey) data).getKeyAsLong());
		return BYTES_PER_LONG;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getCurrentDataVersion() {
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int deserializeKeySize(final ByteBuffer buffer) {
		return BYTES_PER_LONG;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(
			final ByteBuffer buffer,
			final int dataVersion,
			final VirtualKey<VirtualLongKey> keyToCompare) throws IOException {

		return buffer.getLong() == ((VirtualLongKey) keyToCompare).getKeyAsLong();
	}
}

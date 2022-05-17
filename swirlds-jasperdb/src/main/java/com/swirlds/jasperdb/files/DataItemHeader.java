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

package com.swirlds.jasperdb.files;

import java.util.Objects;

/**
 * Each data item needs a header containing at least a numeric key. The key can be any size from byte to long. The size
 * can be stored for variable size data or this can be constructed with a fixed size.
 */
public final class DataItemHeader {
	/** the size of bytes for the data item, this includes the data item header. */
	private final int sizeBytes;
	/** the key for data item, the key may be smaller than long up to size of long */
	private final long key;

	public DataItemHeader(final int sizeBytes, final long key) {
		this.sizeBytes = sizeBytes;
		this.key = key;
	}

	/**
	 * Get the size of bytes for the data item, this includes the data item header.
	 */
	public int getSizeBytes() {
		return sizeBytes;
	}

	/**
	 * Get the key for data item, the key may be smaller than long up to size of long
	 */
	public long getKey() {
		return key;
	}

	@Override
	public String toString() {
		return "DataItemHeader{" +
				"size=" + sizeBytes +
				", key=" + key +
				'}';
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final DataItemHeader that = (DataItemHeader) o;
		return sizeBytes == that.sizeBytes && key == that.key;
	}

	@Override
	public int hashCode() {
		return Objects.hash(sizeBytes, key);
	}
}

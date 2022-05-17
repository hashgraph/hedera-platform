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

package com.swirlds.common.transaction.internal;

import com.swirlds.common.TransactionType;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.Transaction;

import java.io.IOException;
import java.util.Arrays;

import static com.swirlds.common.io.ExtendedDataOutputStream.getArraySerializedLength;
import static com.swirlds.common.TransactionType.SYS_TRANS_BITS_PER_SECOND;

/** A system transaction giving all avgBytePerSecSent stats (sent as bits per second) */
public class SystemTransactionBitsPerSecond implements Transaction {
	/** class identifier for the purposes of serialization */
	private static final long BPS_CLASS_ID = 0x6922237d8f4dac99L;
	/** current class version */
	private static final int BPS_CLASS_VERSION = 1;

	private long[] avgBitsPerSecSent;

	/**
	 * No-argument constructor used by ConstructableRegistry
	 */
	public SystemTransactionBitsPerSecond() {
	}

	public SystemTransactionBitsPerSecond(final long[] avgBitsPerSecSent) {
		this.avgBitsPerSecSent = avgBitsPerSecSent;
	}

	/**
	 * @return the long array of average bits per second
	 */
	public long[] getAvgBitsPerSecSent() {
		return avgBitsPerSecSent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSize() {
		return getSerializedLength();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TransactionType getTransactionType() {
		return SYS_TRANS_BITS_PER_SECOND;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeLongArray(avgBitsPerSecSent);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.avgBitsPerSecSent = in.readLongArray(SettingsCommon.maxAddressSizeAllowed);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumSupportedVersion() {
		return BPS_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return BPS_CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return BPS_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSerializedLength() {
		return getArraySerializedLength(avgBitsPerSecSent);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final SystemTransactionBitsPerSecond that = (SystemTransactionBitsPerSecond) o;

		return Arrays.equals(avgBitsPerSecSent, that.avgBitsPerSecSent);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(avgBitsPerSecSent);
	}

}

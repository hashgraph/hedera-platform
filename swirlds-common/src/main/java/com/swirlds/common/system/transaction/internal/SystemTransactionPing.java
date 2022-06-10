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

package com.swirlds.common.system.transaction.internal;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.TransactionType;

import java.io.IOException;
import java.util.Arrays;

import static com.swirlds.common.io.streams.AugmentedDataOutputStream.getArraySerializedLength;
import static com.swirlds.common.system.transaction.TransactionType.SYS_TRANS_PING_MICROSECONDS;

/** A system transaction giving all avgPingMilliseconds stats (sent as ping time in microseconds) */
public class SystemTransactionPing implements Transaction {
	/** class identifier for the purposes of serialization */
	private static final long PING_CLASS_ID = 0xe98d3e2c500a6647L;
	/** current class version */
	private static final int PING_CLASS_VERSION = 1;

	private int[] avgPingMilliseconds;

	/**
	 * No-argument constructor used by ConstructableRegistry
	 */
	public SystemTransactionPing() {
	}

	public SystemTransactionPing(final int[] avgPingMilliseconds) {
		this.avgPingMilliseconds = avgPingMilliseconds;
	}

	/**
	 * @return the integer array of average ping values between nodes, in the unit of millisecond
	 */
	public int[] getAvgPingMilliseconds() {
		return avgPingMilliseconds;
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
		return SYS_TRANS_PING_MICROSECONDS;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeIntArray(avgPingMilliseconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.avgPingMilliseconds = in.readIntArray(SettingsCommon.maxAddressSizeAllowed);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumSupportedVersion() {
		return PING_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return PING_CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return PING_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSerializedLength() {
		return getArraySerializedLength(avgPingMilliseconds);
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

		final SystemTransactionPing that = (SystemTransactionPing) o;

		return Arrays.equals(avgPingMilliseconds, that.avgPingMilliseconds);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(avgPingMilliseconds);
	}

}

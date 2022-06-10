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

import com.swirlds.common.io.streams.AugmentedDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.TransactionType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.io.streams.SerializableStreamConstants.BOOLEAN_BYTES;
import static com.swirlds.common.system.transaction.TransactionType.SYS_TRANS_STATE_SIG;
import static com.swirlds.common.system.transaction.TransactionType.SYS_TRANS_STATE_SIG_FREEZE;

/**
 * Every round, the signature of a signed state is put in this transaction
 * and gossiped to other nodes
 */
public class StateSignatureTransaction implements Transaction {
	/** class identifier for the purposes of serialization */
	private static final long SIG_CLASS_ID = 0xaf7024c653caabf4L;
	/** current class version */
	private static final int SIG_CLASS_VERSION = 1;
	/** maximum number of bytes allowed when deserializing signature */
	public static final int MAX_SIGNATURE_BYTES = 1024;

	/** whether this is freeze transaction or just normal signature transaction */
	private boolean isFreeze;

	/** signature of signed state */
	private byte[] stateSignature;

	/** round number of signed state */
	private long lastRoundReceived = 0;

	/**
	 * No-argument constructor used by ConstructableRegistry
	 */
	public StateSignatureTransaction() {
	}

	/**
	 * Create a state signature transaction
	 *
	 * @param isFreeze
	 * 		Whether this a freeze transaction or normal state signature transaction
	 * @param lastRoundReceived
	 * 		The round number of the signed state that this transaction belongs to
	 * @param stateSignature
	 * 		The byte array of signature of the signed state
	 */
	public StateSignatureTransaction(final boolean isFreeze,
			final long lastRoundReceived,
			final byte[] stateSignature) {
		this.isFreeze = isFreeze;
		this.stateSignature = stateSignature;
		this.lastRoundReceived = lastRoundReceived;
	}

	/**
	 * @return whether this a freeze transaction or normal state signature transaction
	 */
	public boolean isFreeze() {
		return isFreeze;
	}

	/**
	 * @return the round number of the signed state that this transaction belongs to
	 */
	public long getLastRoundReceived() {
		return lastRoundReceived;
	}

	/**
	 * @return the byte array of signature of the signed state
	 */
	public byte[] getStateSignature() {
		return stateSignature;
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
		return isFreeze ? SYS_TRANS_STATE_SIG_FREEZE : SYS_TRANS_STATE_SIG;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeBoolean(isFreeze);
		dos.writeByteArray(stateSignature);
		dos.writeLong(lastRoundReceived);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		this.isFreeze = in.readBoolean();
		this.stateSignature = in.readByteArray(MAX_SIGNATURE_BYTES);
		this.lastRoundReceived = in.readLong();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumSupportedVersion() {
		return SIG_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return SIG_CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return SIG_CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getSerializedLength() {
		return BOOLEAN_BYTES        // size of isFreeze
				+ AugmentedDataOutputStream.getArraySerializedLength(
				stateSignature)    // size of signature byte array
				+ Long.BYTES;        // size of lastRoundReceived
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

		final StateSignatureTransaction that = (StateSignatureTransaction) o;

		if (isFreeze != that.isFreeze) {
			return false;
		}
		return Arrays.equals(stateSignature, that.stateSignature);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(isFreeze, stateSignature);
	}

}

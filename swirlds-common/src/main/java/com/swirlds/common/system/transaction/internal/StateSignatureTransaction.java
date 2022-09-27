/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.system.transaction.internal;

import com.swirlds.common.io.streams.AugmentedDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.SystemTransactionType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import static com.swirlds.common.io.streams.SerializableStreamConstants.BOOLEAN_BYTES;
import static com.swirlds.common.system.transaction.SystemTransactionType.SYS_TRANS_STATE_SIG;

/**
 * Every round, the signature of a signed state is put in this transaction
 * and gossiped to other nodes
 */
public final class StateSignatureTransaction extends SystemTransaction {

	/** class identifier for the purposes of serialization */
	private static final long SIG_CLASS_ID = 0xaf7024c653caabf4L;
	/** current class version */
	private static final int SIG_CLASS_VERSION = 1;
	/** maximum number of bytes allowed when deserializing signature */
	public static final int MAX_SIGNATURE_BYTES = 1024;

	/**
	 * whether this is freeze transaction or just normal signature transaction
	 *
	 * @deprecated do not use this variable. It deprecated because there is no need to track state signatures that are
	 * 		for a freeze state separately from regular state signatures. It will be removed in a future release.
	 */
	@Deprecated
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
	 * @param lastRoundReceived
	 * 		The round number of the signed state that this transaction belongs to
	 * @param stateSignature
	 * 		The byte array of signature of the signed state
	 */
	public StateSignatureTransaction(final long lastRoundReceived, final byte[] stateSignature) {
		this.stateSignature = stateSignature;
		this.lastRoundReceived = lastRoundReceived;
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
	public SystemTransactionType getType() {
		return SYS_TRANS_STATE_SIG;
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

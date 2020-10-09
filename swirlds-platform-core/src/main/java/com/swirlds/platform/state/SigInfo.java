/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.platform.state;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.platform.Utilities;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;

/**
 * One signature on a signed state, plus related info. An immutable 4-tuple of (round, member, state hash,
 * signature). It does NOT do defensive copying, so callers should be careful to not modify array elements.
 */
public class SigInfo implements FastCopyable<SigInfo>, SelfSerializable {
	private static final long CLASS_ID = 0xea25a1f0f38a7497L;
	private static final int VERSION_ORIGINAL = 1;
	private static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;
	private static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

	/**
	 * This version number should be used to handle compatibility issues that may arise from any future
	 * changes to this class
	 */
	private long classVersion;
	/** the signed state reflects all events with received round less than or equal to this */
	private long round;
	/** the member who signed the state */
	private long memberId;
	/** the hash of the state that was signed. The signature algorithm may internally hash this hash. */
	private byte[] hash;
	/** the signature */
	private byte[] sig;

	private boolean immutable;

	public SigInfo(long round, long memberId, byte[] hash, byte[] sig) {
		classVersion = 1;
		this.round = round;
		this.memberId = memberId;
		this.hash = hash;
		this.sig = sig;
	}

	/** the default constructor is needed for FastCopyable */
	public SigInfo() {
		classVersion = 1;
	}

	private SigInfo(final SigInfo sourceValue) {
		this.round = sourceValue.getRound();
		this.memberId = sourceValue.getMemberId();
		this.hash = sourceValue.getHash();
		this.sig = sourceValue.getSig();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SigInfo copy() {
		throwIfImmutable();
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(round);
		out.writeLong(memberId);
		out.writeByteArray(hash);
		out.writeByteArray(sig);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		round = in.readLong();
		memberId = in.readLong();
		hash = in.readByteArray(DigestType.SHA_384.digestLength());
		sig = in.readByteArray(SignatureType.RSA.getSignatureLength());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFrom(SerializableDataInputStream inStream) throws IOException {
		classVersion = inStream.readLong();
		round = inStream.readLong();
		memberId = inStream.readLong();
		hash = Utilities.readByteArray(inStream);
		sig = Utilities.readByteArray(inStream);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
	}

	/**
	 * getter for the signed state round that reflects all events received
	 *
	 * @return round
	 */
	public long getRound() {
		return round;
	}

	/**
	 * getter for the member who signed the state
	 *
	 * @return member who signed the state
	 */
	public long getMemberId() {
		return memberId;
	}

	/**
	 * getter for the the hash of the state that was signed
	 *
	 * @return the hash of the state that was signed
	 */
	public byte[] getHash() {
		return hash;
	}

	/**
	 * getter for the signature
	 *
	 * @return the signature
	 */
	public byte[] getSig() {
		return sig;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(SerializableDataInputStream inStream) throws IOException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		SigInfo sigInfo = (SigInfo) o;

		return new EqualsBuilder()
				.append(round, sigInfo.round)
				.append(memberId, sigInfo.memberId)
				.append(hash, sigInfo.hash)
				.append(sig, sigInfo.sig)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(round)
				.append(memberId)
				.append(hash)
				.append(sig)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("classVersion", classVersion)
				.append("round", round)
				.append("memberId", memberId)
				.append("hash", hash)
				.append("sig", sig)
				.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isImmutable() {
		return this.immutable;
	}
}

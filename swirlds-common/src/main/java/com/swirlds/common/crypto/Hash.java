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

package com.swirlds.common.crypto;

import com.swirlds.common.io.BadIOException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

import static com.swirlds.common.CommonUtils.hex;

//Note: There is a another class com.swirlds.platform.Hash that I have marked as deprecated, the plan is
//      that this class replaces that one.
public class Hash implements Comparable<Hash>, SelfSerializable, Serializable {

	private static final long CLASS_ID = 0xf422da83a251741eL;
	private static final int CLASS_VERSION = 1;

	private byte[] value;
	private DigestType digestType;

	public Hash() {
		this(DigestType.SHA_384);
	}

	public Hash(final DigestType digestType) {
		this(new byte[digestType.digestLength()], digestType, true);
	}

	public Hash(final byte[] value) {
		this(value, DigestType.SHA_384);
	}

	public Hash(final byte[] value, final DigestType digestType) {
		this(value, digestType, false);
	}

	public Hash(final Hash other) {
		if (other == null) {
			throw new IllegalArgumentException("other");
		}

		this.digestType = other.digestType;
		this.value = Arrays.copyOf(other.value, other.value.length);
	}

	protected Hash(final byte[] value, final DigestType digestType, final boolean bypassSafetyCheck) {
		this(value, digestType, bypassSafetyCheck, false);
	}

	protected Hash(final byte[] value, final DigestType digestType, final boolean bypassSafetyCheck,
			final boolean shouldCopy) {
		if (value == null) {
			throw new IllegalArgumentException("value");
		}

		if (digestType == null) {
			throw new IllegalArgumentException("digestType");
		}

		if (value.length != digestType.digestLength()) {
			throw new IllegalArgumentException("value: " + value.length);
		}

		this.digestType = digestType;

		final byte[] valuePtr = (shouldCopy) ? Arrays.copyOf(value, value.length) : value;

		if (bypassSafetyCheck) {
			this.value = valuePtr;
		} else {
			setValue(valuePtr);
		}
	}

	public byte[] getValue() {
		return value;
	}

	public void setValue(final byte[] value) {
		this.updateHashValue(value);
	}

	private void updateHashValue(final byte[] value) {
		if (value == null || value.length != digestType.digestLength()) {
			throw new IllegalArgumentException("value");
		}

		// Check for all zeros & stop when first non-zero byte has been encountered
		for (byte b : value) {
			if (b != 0) {
				this.value = value;
				return;
			}
		}

		// We throw an exception here because the value array contained all zero bytes
		throw new EmptyHashValueException("Hash creation failed, hash is array of zeroes");
	}

	public Hash copy() {
		return new Hash(this);
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
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeInt(digestType.id());
		out.writeByteArray(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		final DigestType digestType = DigestType.valueOf(in.readInt());

		if (digestType == null) {
			throw new BadIOException("Invalid DigestType identifier read from the stream");
		}

		this.digestType = digestType;
		this.value = in.readByteArray(digestType.digestLength());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Hash)) {
			return false;
		}

		final Hash that = (Hash) obj;

		return digestType.id() == that.digestType.id() && Arrays.equals(value, that.value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		int ret = 17;
		ret += 31 * ret + Integer.hashCode(digestType.id());
		ret += 31 * ret + Arrays.hashCode(value);

		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(final Hash that) {
		if (this == that) {
			return 0;
		}

		if (that == null) {
			throw new NullPointerException("that");
		}

		int ret = Integer.compare(digestType.id(), that.digestType.id());

		if (ret != 0) {
			return ret;
		}

		return Arrays.compare(value, that.value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return (value == null) ? null : hex(value);
	}

	public DigestType getDigestType() {
		return this.digestType;
	}
}

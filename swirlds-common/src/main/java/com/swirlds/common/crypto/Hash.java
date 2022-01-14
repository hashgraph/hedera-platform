/*
 * (c) 2016-2022 Swirlds, Inc.
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
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.swirlds.common.CommonUtils.hex;

/**
 * A cryptographic hash of some data.
 */
public class Hash implements Comparable<Hash>, SelfSerializable, Serializable {

	private static final long CLASS_ID = 0xf422da83a251741eL;
	private static final int CLASS_VERSION = 1;

	private byte[] value;
	private DigestType digestType;

	/**
	 * Zero arg constructor. Creates a hash without any data using the default digest type ({@link DigestType#SHA_384}).
	 */
	public Hash() {
		this(DigestType.SHA_384);
	}

	/**
	 * Create a hash with a specific digest type but without any hash data.
	 *
	 * @param digestType
	 * 		the digest type
	 */
	public Hash(final DigestType digestType) {
		this(new byte[digestType.digestLength()], digestType, true, false);
	}

	/**
	 * Instantiate a hash with data from a byte array. Uses the default digest type ({@link DigestType#SHA_384}).
	 *
	 * @param value
	 * 		the hash bytes
	 */
	public Hash(final byte[] value) {
		this(value, DigestType.SHA_384);
	}

	/**
	 * Instantiate a hash with data from a byte array with a specific digest type.
	 *
	 * @param value
	 * 		the hash bytes
	 * @param digestType
	 * 		the digest type
	 */
	public Hash(final byte[] value, final DigestType digestType) {
		this(value, digestType, false, false);
	}

	/**
	 * Instantiate a hash with a byte buffer and a digest type.
	 *
	 * @param byteBuffer
	 * 		a buffer that contains the data for this hash
	 * @param digestType
	 * 		the digest type of this hash
	 */
	public Hash(final ByteBuffer byteBuffer, final DigestType digestType) {
		this(digestType);
		byteBuffer.get(this.value);
	}

	/**
	 * Create a hash making a deep copy of another hash.
	 *
	 * @param other
	 * 		the hash to copy
	 */
	public Hash(final Hash other) {
		if (other == null) {
			throw new IllegalArgumentException("other");
		}

		this.digestType = other.digestType;
		this.value = Arrays.copyOf(other.value, other.value.length);
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
	}

	/**
	 * Get the byte array representing the value of the hash.
	 *
	 * @return the hash value
	 */
	public byte[] getValue() {
		return value;
	}

	/**
	 * Get a deep copy of this hash.
	 *
	 * @return a new hash
	 */
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
		return ((value[0] << 24) + (value[1] << 16) + (value[2] << 8) + (value[3]));
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

	/**
	 * Get the digest type of this hash.
	 *
	 * @return the digest type
	 */
	public DigestType getDigestType() {
		return this.digestType;
	}
}

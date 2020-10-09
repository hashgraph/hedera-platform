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

import com.swirlds.common.Transaction;
import com.swirlds.logging.LogMarker;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

public class HashBuilder {

	private MessageDigest digest;
	private DigestType digestType;

	public HashBuilder(final MessageDigest digest) {
		if (digest == null) {
			throw new IllegalArgumentException("digest");
		}

		this.digest = digest;
		detectDigestType();
	}

	public HashBuilder(final DigestType digestType) {
		if (digestType == null) {
			throw new IllegalArgumentException("digestType");
		}

		this.digestType = digestType;
		initializeDigest();
	}

	/**
	 * hash the given long
	 *
	 * @param n
	 * 		the long value to be hashed
	 * @return the HashBuilder object after digesting this long number
	 */
	public HashBuilder update(long n) {
		for (int i = 0; i < Long.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}

		return this;
	}

	/**
	 * hash the given int
	 *
	 * @param n
	 * 		the int value to be hashed
	 * @return the HashBuilder object after digesting this int number
	 */
	public HashBuilder update(int n) {
		for (int i = 0; i < Integer.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}

		return this;
	}

	/**
	 * hash the given array of bytes
	 *
	 * @param t
	 * 		the array of bytes
	 * @param offset
	 * 		the offset to start from in the array of bytes
	 * @param length
	 * 		the number of bytes to use, starting at {@code offset}
	 * @return the HashBuilder object after digesting this int number
	 */
	public HashBuilder update(byte[] t, int offset, int length) {
		if (t == null) {
			update(0);
		} else {
			update(t.length);
			digest.update(t, offset, length);
		}

		return this;
	}


	/**
	 * hash the given Instant
	 *
	 * @param i
	 * 		the Instant object to be hashed
	 * @return the HashBuilder object after digesting this Instant object
	 */
	public HashBuilder update(Instant i) {
		// the instant class consists of only 2 parts, the seconds and the nanoseconds
		update(i.getEpochSecond());
		update(i.getNano());

		return this;
	}

	/**
	 * hash the given array of byte arrays
	 *
	 * @param t
	 * 		the array of byte arrays to be hashed
	 * @return the HashBuilder object after digesting this array
	 */
	public HashBuilder update(byte[][] t) {
		if (t == null) {
			update(0);
		} else {
			update(t.length);
			for (byte[] a : t) {
				if (a == null) {
					update(0);
				} else {
					update(a.length);
					digest.update(a);
				}
			}
		}

		return this;
	}

	/**
	 * hash the given array of bytes, including its length
	 *
	 * @param t
	 * 		the byte arrays to be hashed
	 * @return the HashBuilder object after digesting this array
	 */
	public HashBuilder update(byte[] t) {
		if (t == null) {
			update(0);
		} else {
			update(t.length);
			digest.update(t);
		}

		return this;
	}


	/**
	 * hash the given array of transactions
	 *
	 * @param t
	 * 		the array of Transactions to be hashed
	 * @return the HashBuilder object after digesting this array
	 */
	public HashBuilder update(Transaction[] t) {
		if (t == null) {
			update(0);
		} else {
			update(t.length);
			for (Transaction a : t) {
				if (a == null) {
					update(0);
				} else {
					a.computeDigest(digest);
				}
			}
		}

		return this;
	}

	/**
	 * Temporary method until we change how we hash all objects
	 *
	 * @param hash
	 * 		the Hash object to be hashed
	 * @return the HashBuilder object after digesting this Hash object
	 */
	public HashBuilder update(final Hash hash) {
		if (hash == null) {
			throw new IllegalArgumentException("hash");
		}

		digest.update(hash.getValue());

		return this;
	}

	/**
	 * hash the given array of bytes, not including its length
	 *
	 * @param data
	 * 		the byte arrays to be hashed
	 * @return the HashBuilder object after digesting this array
	 */
	public HashBuilder updateRaw(final byte[] data) {
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("data");
		}

		digest.update(data);
		return this;
	}

	/**
	 * resets the digest
	 *
	 * @return the HashBuilder object after the digest is reset
	 */
	public HashBuilder reset() {
		digest.reset();

		return this;
	}

	/**
	 * @return a mutable Hash
	 */
	public Hash build() {
		return mutable();
	}

	/**
	 * @return a mutable Hash
	 */
	public Hash mutable() {
		return new Hash(digest.digest(), digestType);
	}

	/**
	 * @return an immutable Hash
	 */
	public ImmutableHash immutable() {
		return new ImmutableHash(digest.digest(), digestType);
	}

	/**
	 * initializes the digest object
	 */
	private void initializeDigest() {
		try {
			digest = MessageDigest.getInstance(digestType.algorithmName());
		} catch (NoSuchAlgorithmException ex) {
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}
	}

	/**
	 * checks whether the digestType is valid
	 */
	private void detectDigestType() {
		for (DigestType t : DigestType.values()) {
			if (Objects.equals(t.algorithmName(), digest.getAlgorithm())) {
				digestType = t;
				break;
			}
		}

		if (digestType == null) {
			throw new InvalidDigestTypeException(digest.getAlgorithm());
		}
	}
}

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

package com.swirlds.common.bloom.hasher;

import com.swirlds.common.bloom.BloomHasher;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.common.utility.NonCryptographicHashing.hash64;
import static com.swirlds.common.utility.Units.BYTES_PER_LONG;

/**
 * This class hashes {@link SelfSerializable} objects for a bloom filter.
 *
 * @param <T>
 * 		the type of the object
 */
public class SelfSerializableBloomHasher<T extends SelfSerializable> implements BloomHasher<T> {

	private static final long CLASS_ID = 0xd77d3fbd5065a86fL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void hash(final T element, final long maxHash, final long[] hashes) {
		final Hash hash = CryptoFactory.getInstance().digestSync(element);
		final byte[] hashBytes = hash.getValue();

		int index = 0;

		// Use the data inside the cryptographic hash until it runs out.
		for (; (index + 1) * BYTES_PER_LONG <= hashBytes.length && index < hashes.length; index++) {
			hashes[index] = byteArrayToLong(hashBytes, index * BYTES_PER_LONG);
		}

		// Derive the remaining hashes by performing permutations on the cryptographic hashes.
		final int cryptographicHashCount = index;
		for (; index < hashes.length; index++) {
			hashes[index] = hash64(hashes[index - cryptographicHashCount]);
		}

		// Now that all hash computations are complete, reduce values to the maximum allowed.
		// This is done after initial generation, as we want as much entropy as possible during
		// generation (this step reduces entropy).
		for (index = 0; index < hashes.length; index++) {
			hashes[index] = Math.abs(hashes[index]) % maxHash;
		}
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
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		// no-op
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		// no-op
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}
}

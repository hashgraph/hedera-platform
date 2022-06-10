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
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;

import static com.swirlds.common.utility.NonCryptographicHashing.hash64;

/**
 * A {@link BloomHasher} capable of hashing longs.
 */
public class LongBloomHasher implements BloomHasher<Long> {

	private static final long CLASS_ID = 0x661ac9ecfb26ac2L;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void hash(final Long element, final long maxHash, final long[] hashes) {
		long runningHash = element;
		for (int index = 0; index < hashes.length; index++) {
			runningHash = hash64(runningHash);
			hashes[index] = Math.abs(runningHash) % maxHash;
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

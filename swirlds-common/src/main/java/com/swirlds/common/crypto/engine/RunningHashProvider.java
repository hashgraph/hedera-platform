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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.logging.LogMarker;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calculates and updates a running Hash each time a new RunningHashable is added.
 */
public class RunningHashProvider extends
		CachingOperationProvider<Hash, Hash, Hash, HashBuilder, DigestType> {

	/**
	 * A constant log message used to indicate that a {@code null} value was added as a hash which results in a {@link
	 * IllegalArgumentException} being thrown.
	 */
	private static final String NEW_HASH_NULL = "RunningHashProvider :: newHashToAdd is null";

	/**
	 * update the digest using the given hash
	 *
	 * @param hashBuilder
	 * 		for building hash
	 * @param hash
	 * 		a hash to be digested
	 */
	private static void updateForHash(final HashBuilder hashBuilder, final Hash hash) {
		hashBuilder.update(hash.getClassId());
		hashBuilder.update(hash.getVersion());
		hashBuilder.update(hash);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected HashBuilder handleAlgorithmRequired(DigestType algorithmType) throws NoSuchAlgorithmException {
		return new HashBuilder(MessageDigest.getInstance(algorithmType.algorithmName()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Hash handleItem(final HashBuilder hashBuilder, final DigestType algorithmType,
			final Hash runningHash, Hash newHashToAdd) {
		// newHashToAdd should not be null
		if (newHashToAdd == null) {
			log().trace(LogMarker.TESTING_EXCEPTIONS.getMarker(), NEW_HASH_NULL);
			throw new IllegalArgumentException(NEW_HASH_NULL);
		}

		hashBuilder.reset();

		// we only digest current hash when it is not null
		if (runningHash != null) {
			updateForHash(hashBuilder, runningHash);
		}
		// digest new hash
		updateForHash(hashBuilder, newHashToAdd);

		return hashBuilder.build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash compute(final Hash runningHash, final Hash newHashToAdd,
			final DigestType algorithmType)
			throws NoSuchAlgorithmException {
		return handleItem(loadAlgorithm(algorithmType), algorithmType, runningHash, newHashToAdd);
	}
}

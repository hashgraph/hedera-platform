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

package com.swirlds.common.crypto.engine;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.crypto.SerializableHashable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calculates and updates a running Hash each time a new Hash is added.
 */
public class RunningHashProvider extends
		CachingOperationProvider<Hashable, Hash, Hash, HashBuilder, DigestType> {

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
			final Hashable hashable, Hash newHashToAdd) {

		hashBuilder.reset();

		hashBuilder.update(hashable.getHash() == null ? newHashToAdd.getClassId() : hashable.getHash().getClassId());
		hashBuilder.update(hashable.getHash() == null ? newHashToAdd.getVersion() : hashable.getHash().getVersion());

		// if current hash is null, it denotes initialHash is null,
		// we only digest current hash when it is not null
		if (hashable.getHash() != null) {
			hashBuilder.update(hashable.getHash());
		}

		// newHashToAdd should not be null
		if (newHashToAdd == null) {
			final String msg = "RunningHashProvider :: newHashToAdd is an unexpected null Hash";
			log().trace(CryptoEngine.LOGM_TESTING_EXCEPTIONS, msg);
			throw new IllegalArgumentException(msg);
		}
		hashBuilder.update(newHashToAdd);

		return hashBuilder.build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash compute(final Hashable hashable, final Hash newHashToAdd, final DigestType algorithmType)
			throws NoSuchAlgorithmException {
		return handleItem(loadAlgorithm(algorithmType), algorithmType, hashable, newHashToAdd);
	}
}

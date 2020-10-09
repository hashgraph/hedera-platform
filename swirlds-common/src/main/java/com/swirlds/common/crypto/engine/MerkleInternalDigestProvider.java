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
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.exceptions.IllegalChildHashException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class MerkleInternalDigestProvider extends
		CachingOperationProvider<MerkleInternal, List<Hash>, Hash, HashBuilder, DigestType> {

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
			final MerkleInternal node, List<Hash> childHashes) {

		hashBuilder.reset();

		hashBuilder.update(node.getClassId());
		hashBuilder.update(node.getVersion());
		for (int index = 0; index < childHashes.size(); index++) {
			final Hash childHash = childHashes.get(index);

			if (childHash == null) {
				final MerkleNode childNode = node.getChild(index);
				final String msg = String.format(
						"Child has an unexpected null hash [ parentClass = '%s', childClass = '%s' ]",
						node.getClass().getName(), childNode.getClass().getName());


				log().trace(CryptoEngine.LOGM_TESTING_EXCEPTIONS, msg);
				throw new IllegalChildHashException(msg);
			}

			hashBuilder.update(childHash);
		}

		return hashBuilder.build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hash compute(final MerkleInternal node, final List<Hash> childHashes, final DigestType algorithmType)
			throws NoSuchAlgorithmException {
		return handleItem(loadAlgorithm(algorithmType), algorithmType, node, childHashes);
	}
}

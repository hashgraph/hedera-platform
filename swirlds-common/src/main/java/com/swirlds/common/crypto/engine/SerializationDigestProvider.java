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

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.logging.LogMarker;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SerializationDigestProvider extends
		CachingOperationProvider<SelfSerializable, Void, Hash, HashingOutputStream, DigestType> {
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected HashingOutputStream handleAlgorithmRequired(final DigestType algorithmType)
			throws NoSuchAlgorithmException {
		return new HashingOutputStream(MessageDigest.getInstance(algorithmType.algorithmName()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Hash handleItem(final HashingOutputStream algorithm, final DigestType algorithmType,
			final SelfSerializable item, final Void optionalData) {
		algorithm.resetDigest();//probably not needed, just to be safe
		try (SerializableDataOutputStream out = new SerializableDataOutputStream(algorithm)) {
			out.writeSerializable(item, true);
			out.flush();

			return new Hash(algorithm.getDigest(), algorithmType);
		} catch (IOException ex) {
			throw new CryptographyException(ex, LogMarker.EXCEPTION);
		}
	}

}

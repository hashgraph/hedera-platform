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

package com.swirlds.platform.crypto;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.stream.Signer;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;

/**
 * An instance capable of signing data with the platforms private signing key. This class is not thread safe.
 */
public class PlatformSigner implements Signer, HashSigner {
	private final Signature signature;

	/**
	 * @param keysAndCerts
	 * 		the platform's keys and certificates
	 */
	public PlatformSigner(final KeysAndCerts keysAndCerts)
			throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
		Signature s = Signature.getInstance(CryptoConstants.SIG_TYPE2, CryptoConstants.SIG_PROVIDER);
		s.initSign(keysAndCerts.sigKeyPair().getPrivate());
		this.signature = s;
	}

	@Override
	public byte[] sign(final byte[] data) {
		try {
			signature.update(data);
			return signature.sign();
		} catch (SignatureException e) {
			// this can only occur if this signature object is not initialized properly, which we ensure is done in the
			// constructor. so this can never happen
			throw new CryptographyException(e);
		}
	}

	@Override
	public byte[] sign(final Hash hash) {
		CommonUtils.throwArgNull(hash, "hash");
		return sign(hash.getValue());
	}
}

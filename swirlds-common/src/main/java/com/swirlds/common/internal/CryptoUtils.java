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

package com.swirlds.common.internal;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public abstract class CryptoUtils {
	/** the type of hash to use */
	private final static String HASH_TYPE = "SHA-384";
	private final static String PRNG_TYPE = "SHA1PRNG";
	private final static String PRNG_PROVIDER = "SUN";

	// return the MessageDigest for the type of hash function used throughout the code
	public static MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance(HASH_TYPE);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create an instance of the default deterministic {@link SecureRandom}
	 *
	 * @return an instance of {@link SecureRandom}
	 * @throws NoSuchProviderException
	 * 		if the security provider is not available on the system
	 * @throws NoSuchAlgorithmException
	 * 		if the algorithm is not available on the system
	 */
	public static SecureRandom getDetRandom() throws NoSuchProviderException, NoSuchAlgorithmException {
		return SecureRandom.getInstance(PRNG_TYPE, PRNG_PROVIDER);
	}

}

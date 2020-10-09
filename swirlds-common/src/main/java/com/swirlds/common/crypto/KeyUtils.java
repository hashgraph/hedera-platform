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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

public class KeyUtils {
	public static KeyPair generateKeyPair(KeyType keyType, int keySize, SecureRandom secureRandom) {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyType.getAlgorithmName(), keyType.getProvider());
			keyGen.initialize(keySize, secureRandom);
			return keyGen.generateKeyPair();
		} catch (Exception e) {
			// should never happen
			throw new RuntimeException(e);
		}
	}
}



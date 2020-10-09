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

import java.security.PublicKey;

public enum KeyType {
	EC(1, "SunEC"),
	RSA(2, "SunRsaSign");

	private final int algorithmIdentifier;
	private final String provider;

	KeyType(int algorithmIdentifier, String provider) {
		this.algorithmIdentifier = algorithmIdentifier;
		this.provider = provider;
	}

	String getAlgorithmName() {
		return name();
	}

	public int getAlgorithmIdentifier() {
		return algorithmIdentifier;
	}

	public String getProvider() {
		return provider;
	}

	static KeyType getKeyType(int algorithmIdentifier) {
		switch (algorithmIdentifier) {
			case 1:
				return EC;
			case 2:
				return RSA;
		}
		return null;
	}

	static KeyType getKeyType(PublicKey key) {
		switch (key.getAlgorithm()) {
			case "EC":
				return KeyType.EC;
			case "RSA":
				return KeyType.RSA;
		}
		throw new IllegalArgumentException(key.getAlgorithm() + " is not a known key type!");
	}
}

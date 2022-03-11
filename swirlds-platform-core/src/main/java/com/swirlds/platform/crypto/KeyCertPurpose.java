/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.crypto;

/**
 * Denotes which of the three purposes a key or certificate serves
 */
public enum KeyCertPurpose {
	SIGNING("s"),
	AGREEMENT("a"),
	ENCRYPTION("e");

	/** the prefix used for certificate names */
	private final String prefix;

	KeyCertPurpose(final String prefix) {
		this.prefix = prefix;
	}

	/**
	 * @param memberName
	 * 		the name of the required member
	 * @return the name of the key or certificate used in a KeyStore for this member and key type
	 */
	public String storeName(final String memberName) {
		return prefix + "-" + memberName;
	}
}

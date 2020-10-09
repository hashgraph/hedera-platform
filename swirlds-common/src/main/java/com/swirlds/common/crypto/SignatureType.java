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


/**
 * The type of cryptographic algorithm used to create a signature.
 */
public enum SignatureType {
	/** An Ed25519 signature which uses a SHA-512 hash and a 32 byte public key */
	ED25519("NONEwithED25519", "ED25519", "", 64),

	/** An RSA signature as specified by the FIPS 186-4 standards */
	RSA("SHA384withRSA", "RSA", "SunRsaSign", 384),

	/** An Elliptical Curve based signature as specified by the FIPS 186-4 standards */
	ECDSA("SHA384withECDSA", "EC", "SunEC", 104);

	/**
	 * Enum constructor used to initialize the values with the algorithm characteristics.
	 *
	 * @param signingAlgorithm
	 * 		the JCE signing algorithm name
	 * @param keyAlgorithm
	 * 		the JCE key generation algorithm name
	 * @param provider
	 * 		the JCE provider name
	 * @param signatureLength
	 * 		The length of the signature in bytes
	 */
	SignatureType(final String signingAlgorithm, final String keyAlgorithm, final String provider,
			final int signatureLength) {
		this.signingAlgorithm = signingAlgorithm;
		this.keyAlgorithm = keyAlgorithm;
		this.provider = provider;
		this.signatureLength = signatureLength;
	}

	/**
	 * the JCE name for the signing algorithm
	 */
	private final String signingAlgorithm;

	/**
	 * the JCE name for the key generation algorithm
	 */
	private final String keyAlgorithm;

	/**
	 * the JCE name for the cryptography provider
	 */
	private final String provider;

	/**
	 * The length of the signature in bytes
	 */
	private final int signatureLength;

	private static final SignatureType[] ORDINAL_LOOKUP = values();

	/**
	 * the max length of digest output in bytes among all SignatureType
	 */
	private static final int maxLength;

	static {
		int maxLengthTmp = 0;
		for (SignatureType signatureType : SignatureType.values()) {
			maxLengthTmp = Math.max(maxLengthTmp, signatureType.signatureLength);
		}
		maxLength = maxLengthTmp;
	}

	/**
	 * Getter to retrieve the JCE name for the signing algorithm.
	 *
	 * @return the JCE signing algorithm name
	 */
	public String signingAlgorithm() {
		return this.signingAlgorithm;
	}

	/**
	 * Getter to retrieve the JCE name for the key generation algorithm.
	 *
	 * @return the JCE key generation algorithm name
	 */
	public String keyAlgorithm() {
		return this.keyAlgorithm;
	}

	/**
	 * Getter to retrieve the JCE name for the cryptography provider.
	 *
	 * @return the JCE provider name
	 */
	public String provider() {
		return this.provider;
	}

	/**
	 * Getter to retrieve the length of the signature in bytes
	 *
	 * @return the length of the signature
	 */
	public int getSignatureLength() {
		return signatureLength;
	}

	/**
	 * Translates an ordinal position into an enumeration value.
	 *
	 * @param ordinal
	 * 		the ordinal value to be translated
	 * @param defaultValue
	 * 		the default enumeration value to return if the {@code ordinal} cannot be found
	 * @return the enumeration value related to the given ordinal or the default value if the ordinal is not
	 * 		found
	 */
	public static SignatureType from(final int ordinal,
			final SignatureType defaultValue) {
		if (ordinal < 0 || ordinal >= ORDINAL_LOOKUP.length) {
			return defaultValue;
		}

		return ORDINAL_LOOKUP[ordinal];
	}

	/**
	 * Getter to retrieve the max length of digest output in bytes among all SignatureType
	 *
	 * @return the max length of digest output in bytes among all SignatureType
	 */
	public static int getMaxLength() {
		return maxLength;
	}
}

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

import java.time.Instant;

public final class CryptoConstants {
	// number of bytes in a hash
	public static final int HASH_SIZE_BYTES = 48; // 384 bits (= 3*128)
	// size (in bits) of a public or private key
	public static final int SIG_KEY_SIZE_BITS = 3072;
	public static final int ENC_KEY_SIZE_BITS = 384; // 3*128 bits
	public static final int AGR_KEY_SIZE_BITS = 384; // 3*128 bits
	// max number of bytes in a signature
	// this might be as high as 16+2*ceiling(KEY_SIZE_BITS/8), but is 8 less than that here
	public static final int SIG_SIZE_BYTES = 384;
	// size of each symmetric key, in bytes
	public static final int SYM_KEY_SIZE_BYTES = 32; // 256 bits
	// the algorithms and providers to use (AGR is key agreement, ENC is encryption, SIG is signatures)
	public static final String AGR_TYPE = "EC";
	public static final String AGR_PROVIDER = "SunEC";
	public static final String ENC_TYPE = "EC";
	public static final String ENC_PROVIDER = "SunEC";
	public static final String SIG_TYPE1 = "RSA"; // or SHA384withRSA
	public static final String SIG_PROVIDER = "SunRsaSign";
	public static final String SIG_TYPE2 = "SHA384withRSA"; // or RSA
	/** this is the only TLS protocol we will allow */
	public static final String TLS_SUITE =  "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" ;
	// certificate settings
	public static final Instant DEFAULT_VALID_FROM = Instant.parse("2000-01-01T00:00:00Z");
	public static final Instant DEFAULT_VALID_TO = Instant.parse("2100-01-01T00:00:00Z");
	// SSL settings
	public static final String KEY_MANAGER_FACTORY_TYPE = "SunX509";// recommended by FIPS
	public static final String TRUST_MANAGER_FACTORY_TYPE = "SunX509";// recommended by FIPS
	public static final String SSL_VERSION = "TLSv1.2";
	// keystore settings
	public static final String KEYSTORE_TYPE = "pkcs12";
	public static final String PUBLIC_KEYS_FILE = "public.pfx";

	private CryptoConstants() {
	}
}

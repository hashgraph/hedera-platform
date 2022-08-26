/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.crypto;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.internal.CryptographySettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SigningProviderTest {
	private final static CryptographySettings engineSettings = CryptographySettings.getDefaultSettings();
	private static Cryptography cryptoProvider;
	private static int TEST_TIMES = 100;

	@BeforeAll
	public static void startup() throws NoSuchAlgorithmException {
		assertNotNull(engineSettings, "Check crptyo engine settings");
		assertTrue(engineSettings.computeCpuDigestThreadCount() > 1, "Check cpu digest thread count");
		cryptoProvider = CryptoFactory.getInstance();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 32, 500, 1000 })
	void ECDSASigningProviderTest(int transactionSize) throws Exception {
		SplittableRandom random = new SplittableRandom();
		final ECDSASigningProvider ecdsaSigningProvider = new ECDSASigningProvider();
		assertTrue(ecdsaSigningProvider.isAlgorithmAvailable(), "Check ECDSA is supported");
		assertEquals(EcdsaUtils.SIGNATURE_LENGTH, ecdsaSigningProvider.getSignatureLength(), "Check signature length");
		assertEquals(88, ecdsaSigningProvider.getPrivateKeyBytes().length, "Check key length");

		for (int i = 0; i < TEST_TIMES; i++) {

			final byte[] msg = new byte[transactionSize];
			random.nextBytes(msg);
			final byte[] signature = ecdsaSigningProvider.sign(msg);
			assertTrue(cryptoProvider.verifySync(msg, signature, ecdsaSigningProvider.getPublicKeyBytes(),
					SignatureType.ECDSA_SECP256K1), "check ECDSA result");
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 32, 500, 1000 })
	void ED25519SigningProviderTest(int transactionSize) throws Exception {
		SplittableRandom random = new SplittableRandom();
		final ED25519SigningProvider ed25519SigningProvider = new ED25519SigningProvider();
		assertTrue(ed25519SigningProvider.isAlgorithmAvailable(), "Check ED25519 is supported");
		assertEquals(ED25519SigningProvider.SIGNATURE_LENGTH, ed25519SigningProvider.getSignatureLength(),
				"Check signature length");
		assertEquals(ED25519SigningProvider.PRIVATE_KEY_LENGTH, ed25519SigningProvider.getPrivateKeyBytes().length,
				"Check key length");
		for (int i = 0; i < TEST_TIMES; i++) {

			final byte[] msg = new byte[transactionSize];
			random.nextBytes(msg);
			final byte[] signature = ed25519SigningProvider.sign(msg);
			assertTrue(cryptoProvider.verifySync(msg, signature, ed25519SigningProvider.getPublicKeyBytes(),
					SignatureType.ED25519), "check ED25519 result");
		}
	}

}

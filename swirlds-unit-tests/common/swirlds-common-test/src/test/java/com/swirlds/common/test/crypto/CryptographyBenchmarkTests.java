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
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Message;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptographyBenchmarkTests {
	private static Cryptography cryptoProvider;

	@BeforeAll
	static void startup() {
		cryptoProvider = CryptoFactory.getInstance();
	}

	private record TransactionComponents(byte[] message, byte[] publicKey, byte[] signature) {
	}

	private record BenchmarkStats(long min, long max, long average, long median) {
	}

	private static TransactionComponents extractComponents(final TransactionSignature signature) {
		final ByteBuffer buffer = ByteBuffer.wrap(signature.getContentsDirect());
		final byte[] message = new byte[signature.getMessageLength()];
		final byte[] publicKey = new byte[signature.getPublicKeyLength()];
		final byte[] signatureBytes = new byte[signature.getSignatureLength()];

		buffer
				.position(signature.getMessageOffset()).get(message)
				.position(signature.getPublicKeyOffset()).get(publicKey)
				.position(signature.getSignatureOffset()).get(signatureBytes);

		return new TransactionComponents(message, signatureBytes, publicKey);
	}

	private static long median(final ArrayList<Long> values) {
		final int middle = values.size() / 2;
		if (values.size() % 2 == 1) {
			return values.get(middle);
		} else {
			return (values.get(middle - 1) + values.get(middle)) / 2;
		}
	}

	private static BenchmarkStats calculateStats(final ArrayList<Long> values) {
		Collections.sort(values);

		long sum = 0;
		for (final long value : values) {
			sum += value;
		}

		return new BenchmarkStats(values.get(0), values.get(values.size() - 1), sum / values.size(), median(values));
	}

	@Test
	@Tag(TestTypeTags.PERFORMANCE)
	@DisplayName("Verify Ed25519")
	void verifyEd25519() {
		final int count = 50_000;
		final SignaturePool ed25519SignaturePool = new SignaturePool(count, 100, true);
		final TransactionSignature[] signatures = new TransactionSignature[count];

		final ArrayList<Long> verificationTimes = new ArrayList<>();

		for (int i = 0; i < signatures.length; i++) {
			signatures[i] = ed25519SignaturePool.next();
			final TransactionComponents transactionComponents = extractComponents(signatures[i]);

			final long startTime = System.nanoTime();
			cryptoProvider.verifySync(
					transactionComponents.message,
					transactionComponents.publicKey,
					transactionComponents.signature,
					SignatureType.ED25519);
			final long endTime = System.nanoTime();

			// discard first values, since they take a long time and aren't indicative of actual performance
			if (i > 100) {
				verificationTimes.add(endTime - startTime);
			}
		}

		final BenchmarkStats benchmarkStats = calculateStats(verificationTimes);

		System.out.println("======= Ed25519 Verification =======");
		System.out.println("Max: " + benchmarkStats.max / 1000 + " us");
		System.out.println("Min: " + benchmarkStats.min / 1000 + " us");
		System.out.println("Average: " + benchmarkStats.average / 1000 + " us");
		System.out.println("Median: " + benchmarkStats.median / 1000 + " us");
		System.out.println();

		assertTrue(benchmarkStats.min / 1000 < 175, "Min verification time is too slow");
		assertTrue(benchmarkStats.average / 1000 < 200, "Average verification time is too slow");
		assertTrue(benchmarkStats.median / 1000 < 200, "Median verification time is too slow");
	}

	@Test
	@Tag(TestTypeTags.PERFORMANCE)
	@DisplayName("Verify EcdsaSecp256k1")
	void verifyEcdsaSecp256k1() {
		final int count = 50_000;
		final EcdsaSignedTxnPool ecdsaSignaturePool = new EcdsaSignedTxnPool(count, 64);
		final TransactionSignature[] signatures = new TransactionSignature[count];

		final ArrayList<Long> verificationTimes = new ArrayList<>();

		for (int i = 0; i < signatures.length; i++) {
			signatures[i] = ecdsaSignaturePool.next();
			final TransactionComponents transactionComponents = extractComponents(signatures[i]);

			final long startTime = System.nanoTime();
			cryptoProvider.verifySync(
					transactionComponents.message,
					transactionComponents.publicKey,
					transactionComponents.signature,
					SignatureType.ECDSA_SECP256K1);
			final long endTime = System.nanoTime();

			// discard first values, since they take a long time and aren't indicative of actual performance
			if (i > 100) {
				verificationTimes.add(endTime - startTime);
			}
		}

		final BenchmarkStats benchmarkStats = calculateStats(verificationTimes);

		System.out.println("==== EcdsaSecp256k1 Verification ====");
		System.out.println("Max: " + benchmarkStats.max / 1000 + " us");
		System.out.println("Min: " + benchmarkStats.min / 1000 + " us");
		System.out.println("Average: " + benchmarkStats.average / 1000 + " us");
		System.out.println("Median: " + benchmarkStats.median / 1000 + " us");
		System.out.println();

		assertTrue(benchmarkStats.min / 1000 < 250, "Min verification time is too slow");
		assertTrue(benchmarkStats.average / 1000 < 275, "Average verification time is too slow");
		assertTrue(benchmarkStats.median / 1000 < 275, "Median verification time is too slow");
	}

	@Test
	@Tag(TestTypeTags.PERFORMANCE)
	@DisplayName("SHA384 Hash")
	void sha384Hash() throws NoSuchAlgorithmException {
		final int count = 5_000_000;
		final MessageDigestPool digestPool = new MessageDigestPool(count, 100);
		final Message[] messages = new Message[count];

		final ArrayList<Long> hashTimes = new ArrayList<>();

		for (int i = 0; i < messages.length; i++) {
			messages[i] = digestPool.next();

			final byte[] payload = messages[i].getPayloadDirect();

			final long startTime = System.nanoTime();
			cryptoProvider.digestSync(payload, DigestType.SHA_384);
			final long endTime = System.nanoTime();

			// discard first values, since they take a long time and aren't indicative of actual performance
			if (i > 100) {
				hashTimes.add(endTime - startTime);
			}
		}

		final BenchmarkStats benchmarkStats = calculateStats(hashTimes);

		System.out.println("======== SHA384 Hashing ========");
		System.out.println("Max: " + benchmarkStats.max + " ns");
		System.out.println("Min: " + benchmarkStats.min + " ns");
		System.out.println("Average: " + benchmarkStats.average + " ns");
		System.out.println("Median: " + benchmarkStats.median + " ns");
		System.out.println();

		assertTrue(benchmarkStats.min < 375, "Min hashing time is too slow");
		assertTrue(benchmarkStats.average < 500, "Average hashing time is too slow");
		assertTrue(benchmarkStats.median < 500, "Median hashing time is too slow");
	}
}

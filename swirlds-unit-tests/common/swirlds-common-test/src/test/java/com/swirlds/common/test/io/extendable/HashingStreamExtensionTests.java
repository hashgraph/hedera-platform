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

package com.swirlds.common.test.io.extendable;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.extendable.extensions.HashingStreamExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Random;

import static com.swirlds.common.io.extendable.ExtendableOutputStream.extendOutputStream;
import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("HashingStreamExtension Tests")
class HashingStreamExtensionTests {

	@Test
	@DisplayName("Input Stream Sanity Test")
	void inputStreamSanityTest() throws IOException {
		StreamSanityChecks.inputStreamSanityCheck((final InputStream base) ->
				new ExtendableInputStream(base, new HashingStreamExtension(DigestType.SHA_384)));

		StreamSanityChecks.inputStreamSanityCheck((final InputStream base) -> {
			final HashingStreamExtension extension = new HashingStreamExtension(DigestType.SHA_384);
			extension.startHashing();
			return new ExtendableInputStream(base, extension);
		});
	}

	@Test
	@DisplayName("Output Stream Sanity Test")
	void outputStreamSanityTest() throws IOException {
		StreamSanityChecks.outputStreamSanityCheck((final OutputStream base) ->
				new ExtendableOutputStream(base, new HashingStreamExtension(DigestType.SHA_384)));

		StreamSanityChecks.outputStreamSanityCheck((final OutputStream base) -> {
			final HashingStreamExtension extension = new HashingStreamExtension(DigestType.SHA_384);
			extension.startHashing();
			return new ExtendableOutputStream(base, extension);
		});
	}

	@Test
	@DisplayName("Input Stream Test")
	void inputStreamTest() throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
		final int size = 1024 * 1024;

		final Random random = getRandomPrintSeed();
		final byte[] bytes = new byte[size];
		random.nextBytes(bytes);

		final InputStream byteIn = new ByteArrayInputStream(bytes);
		final HashingStreamExtension extension = new HashingStreamExtension(DigestType.SHA_384);
		extension.startHashing();
		final InputStream in = new ExtendableInputStream(byteIn, extension);

		int remaining = size;

		while (remaining > 0) {
			if (random.nextBoolean()) {
				in.read();
				remaining--;
			}

			if (random.nextBoolean()) {
				in.read(new byte[1024], 0, 1024);
				remaining -= 1024;
			}

			if (random.nextBoolean()) {
				in.readNBytes(1024);
				remaining -= 1024;
			}

			if (random.nextBoolean()) {
				in.readNBytes(new byte[1024], 0, 1024);
				remaining -= 1024;
			}
		}

		final Hash computedHash = extension.finishHashing();

		final MessageDigest digest = MessageDigest.getInstance(
				DigestType.SHA_384.algorithmName(), DigestType.SHA_384.provider());

		digest.update(bytes);
		final Hash expectedHash = new Hash(digest.digest(), DigestType.SHA_384);

		assertEquals(expectedHash, computedHash, "hash should match");
	}

	@Test
	@DisplayName("Output Stream Test")
	void outputStreamTest() throws IOException, NoSuchAlgorithmException, NoSuchProviderException {
		final int size = 1024 * 1024;

		final Random random = getRandomPrintSeed();

		final byte[] bytes = new byte[size];
		random.nextBytes(bytes);

		final OutputStream byteOut = new ByteArrayOutputStream();
		final HashingStreamExtension extension = new HashingStreamExtension(DigestType.SHA_384);
		extension.startHashing();
		final CountingStreamExtension counter = new CountingStreamExtension();
		final OutputStream out = extendOutputStream(byteOut, extension, counter);

		int index = 0;

		while (size - index > 0) {
			if (random.nextBoolean()) {
				out.write(bytes[index]);
				index++;
			}

			if (random.nextBoolean()) {
				final int bytesToWrite = Math.min(1024, size - index);
				out.write(bytes, index, bytesToWrite);
				index += bytesToWrite;
			}
		}

		out.flush();

		assertEquals(size, counter.getCount());

		final Hash computedHash = extension.finishHashing();

		final MessageDigest digest = MessageDigest.getInstance(
				DigestType.SHA_384.algorithmName(), DigestType.SHA_384.provider());

		digest.update(bytes);
		final Hash expectedHash = new Hash(digest.digest(), DigestType.SHA_384);

		assertEquals(expectedHash, computedHash, "hash should match");
	}

}

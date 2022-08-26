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

import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.internal.CryptoUtils;
import com.swirlds.common.test.io.InputOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SerializablePublicKeyTests {

	@ParameterizedTest
	@MethodSource("keyTypeProvider")
	void serializeDeserialize(String keyType, int keySize, boolean writeClassId) throws NoSuchAlgorithmException,
			NoSuchProviderException,
			IOException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyType);
		keyGen.initialize(keySize, CryptoUtils.getDetRandom());
		KeyPair keyPair = keyGen.generateKeyPair();

		SerializablePublicKey original = new SerializablePublicKey(keyPair.getPublic());
		InputOutputStream io = new InputOutputStream();
		io.getOutput().writeSerializable(original, writeClassId);
		io.startReading();
		SerializablePublicKey copy = io.getInput().readSerializable(writeClassId, SerializablePublicKey::new);

		assertEquals(original, copy);
		io.close();
	}

	static Stream<Arguments> keyTypeProvider() {
		return Stream.of(
				arguments("RSA", 3072, false),
				arguments("EC", 384, false)
		);
	}
}

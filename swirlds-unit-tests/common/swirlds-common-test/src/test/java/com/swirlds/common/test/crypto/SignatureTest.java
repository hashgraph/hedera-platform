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

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static com.swirlds.common.test.io.SerializationUtils.serializeDeserialize;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignatureTest {
	@BeforeAll
	public static void setUp() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructables("com.swirlds.common.crypto");
	}

	@Test
	public void serializeDeserializeTest() throws IOException {
		SignatureType signatureType = SignatureType.RSA;
		byte[] sigBytes = new byte[signatureType.signatureLength()];
		ThreadLocalRandom.current().nextBytes(sigBytes);
		Signature signature = new Signature(signatureType, sigBytes);
		Signature deserialized = serializeDeserialize(signature);
		assertEquals(signature, deserialized);
	}
}

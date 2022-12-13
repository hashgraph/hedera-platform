/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

public class KeyUtils {
    public static KeyPair generateKeyPair(KeyType keyType, int keySize, SecureRandom secureRandom) {
        try {
            KeyPairGenerator keyGen =
                    KeyPairGenerator.getInstance(keyType.getAlgorithmName(), keyType.getProvider());
            keyGen.initialize(keySize, secureRandom);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException(e);
        }
    }
}

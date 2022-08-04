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

package com.swirlds.common.crypto;

import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.crypto.internal.CryptographySettings;

/**
 * Public factory implementation from which all {@link Cryptography} instances should be acquired.
 */
public final class CryptoFactory {

	/**
	 * The singleton instance of the {@link Cryptography} interface.
	 */
	private static volatile Cryptography cryptography;

	/**
	 * The current settings used to create the {@link Cryptography} instance. Changes made after the first call to the
	 * {@link #getInstance()} method will be silently ignored.
	 */
	private static volatile CryptographySettings engineSettings = CryptographySettings.getDefaultSettings();

	/**
	 * Private constructor to prevent instantiation.
	 */
	private CryptoFactory() {
		// throw here to ensure we never instantiate this static factory class
		throw new UnsupportedOperationException();
	}

	/**
	 * Registers settings to be used when the {@link Cryptography} instance is created. If the {@link Cryptography}
	 * instance has already been created then this method call will attempt to update the settings.
	 *
	 * @param settings
	 * 		the settings to be used
	 */
	public static synchronized void configure(final CryptographySettings settings) {
		engineSettings = settings;

		if (cryptography != null && (cryptography instanceof CryptoEngine)) {
			((CryptoEngine) cryptography).setSettings(engineSettings);
		}
	}

	/**
	 * Getter for the {@link Cryptography} singleton. Initializes the singleton if not already created with the either
	 * the {@link CryptographySettings#getDefaultSettings()} or with the {@link CryptographySettings} provided via the
	 * {@link #configure(CryptographySettings)} method.
	 *
	 * @return the {@link Cryptography} singleton
	 */
	public static synchronized Cryptography getInstance() {
		if (cryptography == null) {
			cryptography = new CryptoEngine(engineSettings);
		}

		return cryptography;
	}
}

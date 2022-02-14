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

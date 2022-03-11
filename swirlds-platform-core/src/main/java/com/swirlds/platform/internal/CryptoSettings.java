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

package com.swirlds.platform.internal;

import com.swirlds.common.crypto.internal.CryptographySettings;

public class CryptoSettings extends SubSetting implements CryptographySettings {

	/**
	 * the ratio of simultaneous CPU threads to utilize for signature verification
	 */
	public double cpuVerifierThreadRatio = 0.5;

	/**
	 * the ratio of simultaneous CPU threads to utilize for hashing
	 */
	public double cpuDigestThreadRatio = 0.5;

	/**
	 * the fixed size of the CPU verifier queue
	 */
	public int cpuVerifierQueueSize = 100;

	/**
	 * the fixed size of the CPU hashing queue
	 */
	public int cpuDigestQueueSize = 100;

	/**
	 * should only the CPU be used for cryptography
	 */
	public boolean forceCpu = true;

	/**
	 * the password used to protect the PKCS12 key stores containing the nodes RSA keys
	 */
	public String keystorePassword = CryptoSettings.DEFAULT_KEYSTORE_PASSWORD;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getCpuVerifierThreadRatio() {
		return cpuVerifierThreadRatio;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getCpuDigestThreadRatio() {
		return cpuDigestThreadRatio;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getCpuVerifierQueueSize() {
		return cpuVerifierQueueSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getCpuDigestQueueSize() {
		return cpuDigestQueueSize;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean forceCpu() {
		return forceCpu;
	}

	/**
	 * Returns the password to be used to protected the PKCS12 key store containing the nodes RSA key pairs. This method
	 * safely falls back on default values if no password was provided.
	 *
	 * @return the key store password
	 */
	@Override
	public String getKeystorePassword() {
		return (keystorePassword == null) ? CryptographySettings.DEFAULT_KEYSTORE_PASSWORD : keystorePassword;
	}
}

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

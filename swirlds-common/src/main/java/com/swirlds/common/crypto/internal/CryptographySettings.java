/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.common.crypto.internal;

public interface CryptographySettings {

	/**
	 * The default password to be used when loading or creating the key stores if no explicit configuration exists.
	 */
	String DEFAULT_KEYSTORE_PASSWORD = "password";

	/**
	 * Provides a {@link CryptographySettings} with a reasonable set of defaults.
	 *
	 * @return the default settings
	 */
	static CryptographySettings getDefaultSettings() {
		return new CryptographySettings() {


			@Override
			public double getCpuVerifierThreadRatio() {
				return 0.5;
			}

			@Override
			public double getCpuDigestThreadRatio() {
				return 0.5;
			}

			@Override
			public int getCpuVerifierQueueSize() {
				return 100;
			}

			@Override
			public int getCpuDigestQueueSize() {
				return 100;
			}

			@Override
			public boolean forceCpu() {
				return true;
			}

			@Override
			public String getKeystorePassword() {
				return DEFAULT_KEYSTORE_PASSWORD;
			}
		};
	}

	/**
	 * Returns a value between {@code 0.0} and {@code 1.0} inclusive representing the percentage of cores that should be
	 * used for signature verification.
	 *
	 * @return a value between {@code 0.0} and {@code 1.0} inclusive
	 */
	double getCpuVerifierThreadRatio();

	/**
	 * Returns a value between {@code 0.0} and {@code 1.0} inclusive representing the percentage of cores that should be
	 * used for hash computations.
	 *
	 * @return a value between {@code 0.0} and {@code 1.0} inclusive
	 */
	double getCpuDigestThreadRatio();

	/**
	 * Returns a value greater than zero representing the upper bound of the CPU signature verification queue.
	 *
	 * @return a value greater than {@code 0}
	 */
	int getCpuVerifierQueueSize();

	/**
	 * Returns a value greater than zero representing the upper bound of the CPU hashing queue.
	 *
	 * @return a value greater than {@code 0}
	 */
	int getCpuDigestQueueSize();

	/**
	 * Returns true if only the CPU should be used for cryptography and the GPU should be bypassed.
	 *
	 * @return true if only the CPU should be used, false otherwise
	 */
	boolean forceCpu();

	/**
	 * Returns the password used to protect the PKCS12 key stores containing the node RSA public/private key pairs.
	 *
	 * @return the key store password
	 */
	String getKeystorePassword();

	/**
	 * Calculates the number of threads needed to achieve the CPU core ratio given by {@link
	 * #getCpuVerifierThreadRatio()}.
	 *
	 * @return the number of threads to be allocated
	 */
	default int computeCpuVerifierThreadCount() {
		final int numberOfCores = Runtime.getRuntime().availableProcessors();
		final double interimThreadCount = Math.ceil(numberOfCores * getCpuVerifierThreadRatio());

		return (interimThreadCount >= 1.0) ? (int) interimThreadCount : 1;
	}

	/**
	 * Calculates the number of threads needed to achieve the CPU core ratio given by {@link
	 * #getCpuDigestThreadRatio()}.
	 *
	 * @return the number of threads to be allocated
	 */
	default int computeCpuDigestThreadCount() {
		final int numberOfCores = Runtime.getRuntime().availableProcessors();
		final double interimThreadCount = Math.ceil(numberOfCores * getCpuDigestThreadRatio());

		return (interimThreadCount >= 1.0) ? (int) interimThreadCount : 1;
	}

}

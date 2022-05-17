/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.crypto.engine;

import com.goterl.lazysodium.interfaces.Sign;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;

import java.security.NoSuchAlgorithmException;

/**
 * For Internal Use Only. This class will be deprecated and removed once the Platform transitions to a minimum
 * version requirement of JDK 15 or higher. This is short term stop gap solution to address the lack of ED25519 support
 * in older version of the JDK.
 */
public class DelegatingVerificationProvider
		extends OperationProvider<TransactionSignature, Void, Boolean, Object, SignatureType> {

	private final Ed25519VerificationProvider ed25519VerificationProvider;
	private final EcdsaSecp256k1VerificationProvider ecdsaSecp256k1VerificationProvider;

	public DelegatingVerificationProvider(final Ed25519VerificationProvider ed25519VerificationProvider,
			final EcdsaSecp256k1VerificationProvider ecdsaSecp256k1VerificationProvider) {
		this.ed25519VerificationProvider = ed25519VerificationProvider;
		this.ecdsaSecp256k1VerificationProvider = ecdsaSecp256k1VerificationProvider;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Object loadAlgorithm(final SignatureType algorithmType) throws NoSuchAlgorithmException {
		switch (algorithmType) {
			case ECDSA_SECP256K1:
				return ecdsaSecp256k1VerificationProvider.loadAlgorithm(algorithmType);
			case ED25519:
				return ed25519VerificationProvider.loadAlgorithm(algorithmType);
			default:
				throw new NoSuchAlgorithmException();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Boolean handleItem(final Object algorithm, final SignatureType algorithmType,
			final TransactionSignature item, final Void unused) throws NoSuchAlgorithmException {
		switch (algorithmType) {
			case ECDSA_SECP256K1:
				final EcdsaSecp256k1Verifier ecdsaSecp256k1Algo = (EcdsaSecp256k1Verifier) algorithm;
				return ecdsaSecp256k1VerificationProvider.handleItem(ecdsaSecp256k1Algo, algorithmType, item, unused);
			case ED25519:
				final Sign.Native ed25519Algo = (Sign.Native) algorithm;
				return ed25519VerificationProvider.handleItem(ed25519Algo, algorithmType, item, unused);
			default:
				throw new NoSuchAlgorithmException();
		}
	}
}

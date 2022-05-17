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

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.logging.LogMarker;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static com.swirlds.common.CommonUtils.hex;


/**
 * Implementation of a ECDSA_SECP256K1 signature verification provider. This implementation only supports
 * ECDSA_SECP256K1 signatures.
 */
public class EcdsaSecp256k1VerificationProvider
		extends CachingOperationProvider<TransactionSignature, Void, Boolean, EcdsaSecp256k1Verifier, SignatureType> {

	/**
	 * Default Constructor.
	 */
	public EcdsaSecp256k1VerificationProvider() {
		super();
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm.
	 *
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param message
	 * 		the original message that was signed
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key used to verify the signature
	 * @return true if the provided signature is valid; false otherwise
	 */
	protected boolean compute(final byte[] message, final byte[] signature, final byte[] publicKey,
			final SignatureType algorithmType) {
		final EcdsaSecp256k1Verifier loadedAlgorithm;
		
		try {
			loadedAlgorithm = loadAlgorithm(algorithmType);
		} catch (NoSuchAlgorithmException e) {
			throw new CryptographyException(e, LogMarker.ERROR);
		}

		return verified(loadedAlgorithm, algorithmType, message, signature, publicKey);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected EcdsaSecp256k1Verifier handleAlgorithmRequired(
			final SignatureType algorithmType) throws NoSuchAlgorithmException {
		return new EcdsaSecp256k1Verifier();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Boolean handleItem(final EcdsaSecp256k1Verifier algorithm, final SignatureType algorithmType,
			final TransactionSignature item, final Void optionalData) {
		return compute(algorithm, algorithmType, item);
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided signature and algorithm.
	 *
	 * @param algorithm
	 * 		the concrete instance of the required algorithm
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param sig
	 * 		the input signature to be transformed
	 * @return true if the provided signature is valid; false otherwise
	 */
	private boolean compute(final EcdsaSecp256k1Verifier algorithm, final SignatureType algorithmType,
			final TransactionSignature sig) {
		final byte[] payload = sig.getContentsDirect();
		final byte[] expandedPublicKey = sig.getExpandedPublicKey();

		final ByteBuffer buffer = ByteBuffer.wrap(payload);
		final ByteBuffer pkBuffer = (expandedPublicKey != null && expandedPublicKey.length > 0)
				? ByteBuffer.wrap(expandedPublicKey)
				: buffer;

		final byte[] signature = new byte[sig.getSignatureLength()];
		final byte[] publicKey = new byte[sig.getPublicKeyLength()];
		final byte[] message = new byte[sig.getMessageLength()];

		buffer.position(sig.getMessageOffset()).get(message)
				.position(sig.getSignatureOffset()).get(signature);
		pkBuffer.position(sig.getPublicKeyOffset()).get(publicKey);

		return verified(algorithm, algorithmType, message, signature, publicKey);
	}

	private boolean verified(final EcdsaSecp256k1Verifier algorithm, final SignatureType algorithmType,
			final byte[] message, final byte[] signature, final byte[] publicKey) {
		final boolean isValid = algorithm.verify(signature, message, publicKey);

		if (!isValid && log().isDebugEnabled()) {
			log().debug(LogMarker.TESTING_EXCEPTIONS.getMarker(),
					"Adv Crypto Subsystem: Signature Verification Failure for signature " +
							"type {} [ publicKey = {}, signature = {} ]", algorithmType,
					hex(publicKey), hex(signature));
		}

		return isValid;
	}
}

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

package com.swirlds.common.crypto.engine;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.interfaces.Sign;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.logging.LogMarker;

import java.nio.ByteBuffer;

import static com.swirlds.common.CommonUtils.hex;


/**
 * Implementation of an Ed25519 signature verification provider. This implementation only supports
 * Ed25519 signatures and depends on LazySodium (libSodium) for all operations.
 */
public class Ed25519VerificationProvider
		extends OperationProvider<TransactionSignature, Void, Boolean, Sign.Native, SignatureType> {

	/**
	 * The JNI interface to the underlying native libSodium dynamic library. This variable is initialized when this
	 * class is loaded and initialized by the {@link ClassLoader}.
	 */
	private static final Sign.Native algorithm;

	static {
		final SodiumJava sodiumJava = new SodiumJava();
		algorithm = new LazySodiumJava(sodiumJava);
	}

	/**
	 * Default Constructor.
	 */
	public Ed25519VerificationProvider() {
		super();
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm. This
	 * implementation defaults to an Ed25519 signature and is provided for convenience.
	 *
	 * @param message
	 * 		the original message that was signed
	 * @param signature
	 * 		the signature to be verified
	 * @param publicKey
	 * 		the public key used to verify the signature
	 * @return true if the provided signature is valid; false otherwise
	 */
	protected boolean compute(final byte[] message, final byte[] signature,
			final byte[] publicKey) {
		return compute(message, signature, publicKey, SignatureType.ED25519);
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
		final Sign.Native loadedAlgorithm = loadAlgorithm(algorithmType);
		return compute(loadedAlgorithm, algorithmType, message, signature, publicKey);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Sign.Native loadAlgorithm(final SignatureType algorithmType) {
		return algorithm;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Boolean handleItem(final Sign.Native algorithm, final SignatureType algorithmType,
			final TransactionSignature item, final Void optionalData) {
		return compute(algorithm, algorithmType, item);
	}


	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm.
	 *
	 * @param algorithm
	 * 		the concrete instance of the required algorithm
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
	private boolean compute(final Sign.Native algorithm, final SignatureType algorithmType,
			final byte[] message, final byte[] signature, final byte[] publicKey) {
		final boolean isValid = algorithm.cryptoSignVerifyDetached(signature, message, message.length, publicKey);

		if (!isValid && log().isDebugEnabled()) {
			log().debug(LogMarker.TESTING_EXCEPTIONS.getMarker(),
					"Adv Crypto Subsystem: Signature Verification Failure for signature type {} [ publicKey = {}, " +
							"signature = {} ]", algorithmType, hex(publicKey), hex(signature));
		}

		return isValid;
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided sig and algorithm.
	 *
	 * @param algorithm
	 * 		the concrete instance of the required algorithm
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param sig
	 * 		the input signature to be transformed
	 * @return true if the provided signature is valid; false otherwise
	 */
	private boolean compute(final Sign.Native algorithm, final SignatureType algorithmType,
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

		return compute(algorithm, algorithmType, message, signature, publicKey);
	}
}
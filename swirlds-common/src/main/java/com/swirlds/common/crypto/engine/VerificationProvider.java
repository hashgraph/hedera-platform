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

package com.swirlds.common.crypto.engine;

import com.goterl.lazycode.lazysodium.LazySodiumJava;
import com.goterl.lazycode.lazysodium.SodiumJava;
import com.goterl.lazycode.lazysodium.interfaces.Sign;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.SignatureType;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import static com.swirlds.common.CommonUtils.hex;


/**
 * Implementation of a Ed25519 signature verification provider. This implementation only supports Ed25519 signatures and
 * depends on LazySodium (libSodium) for all operations.
 */
public class VerificationProvider extends OperationProvider<TransactionSignature, Void, Boolean, Sign.Native, SignatureType> {

	private static final Sign.Native algorithm;

	static {
		final SodiumJava sodiumJava = new SodiumJava();
		algorithm = new LazySodiumJava(sodiumJava);
	}

	/**
	 * Default Constructor.
	 */
	public VerificationProvider() {
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
	private boolean compute(final byte[] message, final byte[] signature,
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
	private boolean compute(final byte[] message, final byte[] signature, final byte[] publicKey,
			final SignatureType algorithmType) {
		final Sign.Native algorithm = loadAlgorithm(algorithmType);
		return compute(algorithm, algorithmType, message, signature, publicKey);
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm. This
	 * implementation defaults to an Ed25519 signature and is provided for convenience.
	 *
	 * @param payload
	 * 		the full payload containing the original message contents, the signature, and an optional public key
	 * @param expandedPublicKey
	 * 		the optional full public key to be used during verification or null to indicate that this field is not
	 * 		provided
	 * @param signatureOffset
	 * 		the offset of the signature contained in the {@code payload} parameter
	 * @param signatureLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @param publicKeyOffset
	 * 		the offset of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the offset within the {@code expandedPublicKey} parameter
	 * @param publicKeyLength
	 * 		the length of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the length within the {@code expandedPublicKey}
	 * 		parameter
	 * @param messageOffset
	 * 		the offset of the message contained in the {@code payload} parameter
	 * @param messageLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @return true if the provided signature is valid; false otherwise
	 */
	private boolean compute(final byte[] payload, final byte[] expandedPublicKey, final int signatureOffset,
			final int signatureLength, final int publicKeyOffset, final int publicKeyLength, final int messageOffset,
			final int messageLength) throws NoSuchAlgorithmException {
		return compute(payload, expandedPublicKey, signatureOffset, signatureLength, publicKeyOffset, publicKeyLength,
				messageOffset, messageLength, SignatureType.ED25519);
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm.
	 *
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param payload
	 * 		the full payload containing the original message contents, the signature, and an optional public key
	 * @param expandedPublicKey
	 * 		the optional full public key to be used during verification or null to indicate that this field is not
	 * 		provided
	 * @param signatureOffset
	 * 		the offset of the signature contained in the {@code payload} parameter
	 * @param signatureLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @param publicKeyOffset
	 * 		the offset of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the offset within the {@code expandedPublicKey} parameter
	 * @param publicKeyLength
	 * 		the length of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the length within the {@code expandedPublicKey}
	 * 		parameter
	 * @param messageOffset
	 * 		the offset of the message contained in the {@code payload} parameter
	 * @param messageLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @return true if the provided signature is valid; false otherwise
	 */
	private boolean compute(final byte[] payload, final byte[] expandedPublicKey, final int signatureOffset,
			final int signatureLength, final int publicKeyOffset, final int publicKeyLength, final int messageOffset,
			final int messageLength, final SignatureType algorithmType) throws NoSuchAlgorithmException {
		final Sign.Native algorithm = loadAlgorithm(algorithmType);

		return compute(algorithm, algorithmType, payload, expandedPublicKey, signatureOffset, signatureLength,
				publicKeyOffset, publicKeyLength, messageOffset, messageLength);
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
		return compute(algorithm, algorithmType, item.getContentsDirect(), item.getExpandedPublicKeyDirect(),
				item.getSignatureOffset(), item.getSignatureLength(), item.getPublicKeyOffset(),
				item.getPublicKeyLength(), item.getMessageOffset(), item.getMessageLength());
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
			final byte[] message, final byte[] signature,
			final byte[] publicKey) {
		final boolean isValid = algorithm.cryptoSignVerifyDetached(signature, message, message.length, publicKey);

		if (!isValid) {
			log().debug(CryptoEngine.LOGM_TESTING_EXCEPTIONS,
					"Adv Crypto Subsystem: Signature Verification Failure [ publicKey = {}, signature = {} ]",
					hex(publicKey), hex(signature));
		}

		return isValid;
	}


	/**
	 * Computes the result of the cryptographic transformation using the provided item and algorithm.
	 *
	 * @param algorithm
	 * 		the concrete instance of the required algorithm
	 * @param algorithmType
	 * 		the type of algorithm to be used when performing the transformation
	 * @param payload
	 * 		the full payload containing the original message contents, the signature, and an optional public key
	 * @param expandedPublicKey
	 * 		the optional full public key to be used during verification or null to indicate that this field is not
	 * 		provided
	 * @param signatureOffset
	 * 		the offset of the signature contained in the {@code payload} parameter
	 * @param signatureLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @param publicKeyOffset
	 * 		the offset of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the offset within the {@code expandedPublicKey} parameter
	 * @param publicKeyLength
	 * 		the length of the public key contained in the {@code payload} parameter if the {@code expandedPublicKey}
	 * 		parameter is null; otherwise, this refers to the length within the {@code expandedPublicKey}
	 * 		parameter
	 * @param messageOffset
	 * 		the offset of the message contained in the {@code payload} parameter
	 * @param messageLength
	 * 		the length of the signature contained in the {@code payload} parameter
	 * @return true if the provided signature is valid; false otherwise
	 */
	private boolean compute(final Sign.Native algorithm, final SignatureType algorithmType,
			final byte[] payload, final byte[] expandedPublicKey, final int signatureOffset,
			final int signatureLength, final int publicKeyOffset, final int publicKeyLength, final int messageOffset,
			final int messageLength) {
		final ByteBuffer buffer = ByteBuffer.wrap(payload);
		final ByteBuffer pkBuffer = (expandedPublicKey != null && expandedPublicKey.length > 0)
				? ByteBuffer.wrap(expandedPublicKey)
				: buffer;
		final byte[] signature = new byte[signatureLength];
		final byte[] publicKey = new byte[publicKeyLength];
		final byte[] message = new byte[messageLength];

		buffer.position(messageOffset).get(message).position(signatureOffset).get(signature);
		pkBuffer.position(publicKeyOffset).get(publicKey);


		return compute(algorithm, algorithmType, message, signature, publicKey);
	}


}

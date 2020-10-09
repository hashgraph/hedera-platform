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

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Message;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implementation of a message digest provider. This implementation depends on the JCE {@link MessageDigest} providers
 * and supports all algorithms supported by the JVM.
 */
public class DigestProvider extends CachingOperationProvider<Message, Void, byte[], MessageDigest, DigestType> {

	/**
	 * Default Constructor.
	 */
	public DigestProvider() {
		super();
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided message. This
	 * implementation defaults to an SHA-384 message digest and is provided for convenience.
	 *
	 * @param msg
	 * 		the message for which to compute a message digest
	 * @return true if the provided signature is valid; false otherwise
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	private byte[] compute(final byte[] msg) throws NoSuchAlgorithmException {
		return compute(msg, DigestType.SHA_384);
	}

	/**
	 * Computes the result of the cryptographic transformation using the provided message.
	 *
	 * @param msg
	 * 		the message for which to compute a message digest
	 * @param algorithmType
	 * 		the required algorithm to be used when computing the message digest
	 * @return the message digest as an array of the raw bytes
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	private byte[] compute(final byte[] msg, final DigestType algorithmType) throws NoSuchAlgorithmException {
		if (msg == null) {
			throw new IllegalArgumentException("msg");
		}

		return compute(msg, 0, msg.length, algorithmType);
	}

	/**
	 * Computes the result of the cryptographic transformation using the given subset of bytes from the provided
	 * message.  This implementation defaults to an SHA-384 message digest and is provided for convenience.
	 *
	 * @param msg
	 * 		the message for which to compute a message digest
	 * @param offset
	 * 		the starting offset to begin reading
	 * @param length
	 * 		the total number of bytes to read
	 * @return the message digest as an array of the raw bytes
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	private byte[] compute(final byte[] msg, final int offset, final int length) throws NoSuchAlgorithmException {
		return compute(msg, offset, length, DigestType.SHA_384);
	}

	/**
	 * Computes the result of the cryptographic transformation using the given subset of bytes from the provided
	 * message.  This implementation defaults to an SHA-384 message digest and is provided for convenience.
	 *
	 * @param msg
	 * 		the message for which to compute a message digest
	 * @param offset
	 * 		the starting offset to begin reading
	 * @param length
	 * 		the total number of bytes to read
	 * @param algorithmType
	 * 		the message digest algorithm to be used
	 * @return the message digest as an array of the raw bytes
	 * @throws NoSuchAlgorithmException
	 * 		if an implementation of the required algorithm cannot be located or loaded
	 */
	private byte[] compute(final byte[] msg, final int offset, final int length,
			final DigestType algorithmType) throws NoSuchAlgorithmException {
		final MessageDigest algorithm = loadAlgorithm(algorithmType);
		return compute(algorithm, msg, offset, length);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected MessageDigest handleAlgorithmRequired(final DigestType algorithmType) throws
			NoSuchAlgorithmException {
		return MessageDigest.getInstance(algorithmType.algorithmName());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected byte[] handleItem(final MessageDigest algorithm, final DigestType algorithmType, final Message item,
			final Void optionalData) {
		return compute(algorithm, item.getPayloadDirect(), item.getOffset(), item.getLength());
	}


	/**
	 * Computes the result of the cryptographic transformation using the given subset of bytes from the provided
	 * message.  This implementation defaults to an SHA-384 message digest and is provided for convenience.
	 *
	 * @param algorithm
	 * 		the required algorithm implemented to be used
	 * @param msg
	 * 		the message for which to compute a message digest
	 * @param offset
	 * 		the starting offset to begin reading
	 * @param length
	 * 		the total number of bytes to read
	 * @return the message digest as an array of the raw bytes
	 */
	private byte[] compute(final MessageDigest algorithm, final byte[] msg, final int offset, final int length) {
		algorithm.reset();
		algorithm.update(msg, offset, length);

		return algorithm.digest();
	}
}

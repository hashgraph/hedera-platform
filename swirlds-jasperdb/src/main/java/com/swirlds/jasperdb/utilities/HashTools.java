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

package com.swirlds.jasperdb.utilities;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.nio.ByteBuffer;

/**
 * Some helpers for dealing with hashes.
 */
public final class HashTools {
	private static final int CURRENT_SERIALIZATION_VERSION = 1;

	public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;
	public static final int HASH_SIZE_BYTES = DEFAULT_DIGEST.digestLength();

	/**
	 * Gets the version for all hashes written. May represent both the storage format and the digest.
	 *
	 * @return the serialization version being used
	 */
	public static int getSerializationVersion() {
		return CURRENT_SERIALIZATION_VERSION;
	}

	/**
	 * Creates a new {@link ByteBuffer} whose contents are the digest of the given hash.
	 *
	 * @param hash
	 * 		the hash with the digest to put in a byte buffer
	 * @return the byte buffer with the digest of the hash
	 */
	public static ByteBuffer hashToByteBuffer(final Hash hash) {
		final ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
		buf.put(hash.getValue());
		return buf.flip();
	}

	/**
	 * Copies the digest of the given hash into the given {@link ByteBuffer}.
	 *
	 * @param hash
	 * 		the hash with the digest to copy
	 * @param buf
	 * 		the byte buffer to receive the digest of the hash
	 */
	public static void hashToByteBuffer(final Hash hash, final ByteBuffer buf) {
		buf.put(hash.getValue());
	}

	/**
	 * Returns a SHA-384 hash whose digest is the contents of the given {@link Hash}.
	 *
	 * @param buffer
	 * 		the byte buffer whose contents are the desired digest
	 * @param serializationVersion
	 * 		the version of serialization used to create the byte buffer
	 * @return a SHA-384 hash whose digest is the contents of the given buffer
	 */
	public static Hash byteBufferToHash(final ByteBuffer buffer, final int serializationVersion) {
		if (serializationVersion != CURRENT_SERIALIZATION_VERSION) {
			throw new IllegalArgumentException(
					"Current version is " + CURRENT_SERIALIZATION_VERSION + ", got " + serializationVersion);
		}
		return new Hash(buffer, DEFAULT_DIGEST);
	}

	private HashTools() {
		throw new UnsupportedOperationException("Utility Class");
	}
}

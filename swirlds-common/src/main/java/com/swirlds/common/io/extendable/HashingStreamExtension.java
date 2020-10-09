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

package com.swirlds.common.io.extendable;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 * An {@link StreamExtension} that can hash bytes that pass through it. It will not automatically hash all the bytes
 * that pass through. In order to start hashing, the {@link #startHashing()} method needs to be called. Once all the
 * required have passed through, {@link #finishHashing()} should be called.
 */
public class HashingStreamExtension implements StreamExtension {
	/* the digest type used to hash */
	private final DigestType digestType;
	/* the object used for hashing */
	private final MessageDigest digest;
	/** is hashing enabled or not */
	private volatile boolean enabled;

	/**
	 * Constructs a stream extension that can hash bytes that pass though it
	 *
	 * @param digestType
	 * 		the type of hash to be calculated
	 */
	public HashingStreamExtension(DigestType digestType) {
		this.digestType = digestType;
		try {
			this.digest = MessageDigest.getInstance(digestType.algorithmName(), digestType.provider());
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			// Algorithms and providers that are specified in DigestType are the ones we expect to have at runtime. If
			// they are not available, there really is no way to recover from that, so this might as well be an
			// unchecked exception
			throw new RuntimeException(e);
		}
		this.enabled = false;
	}

	/**
	 * Start hashing all the bytes read from this point on until {@link #finishHashing()} is called.
	 */
	public void startHashing() {
		enabled = true;
	}

	/**
	 * Return the hash of the bytes that were read since {@link #startHashing()} was called. This will stop hashing
	 * any further bytes until {@link #startHashing()} is called again.
	 *
	 * @return the hash calculated
	 */
	public Hash finishHashing() {
		return new Hash(digest.digest(), digestType);
	}

	@Override
	public void newByte(int aByte) {
		if (enabled) {
			digest.update((byte) aByte);
		}
	}

	@Override
	public void newBytes(byte[] b, int off, int len) {
		if (enabled) {
			digest.update(b, off, len);
		}
	}
}

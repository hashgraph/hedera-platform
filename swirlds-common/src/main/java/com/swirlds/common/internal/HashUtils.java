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

package com.swirlds.common.internal;

import java.security.MessageDigest;
import java.time.Instant;

public abstract class HashUtils {
	/**
	 * hash the given long
	 *
	 * @param digest
	 * 		the message digest to update
	 * @param n
	 * 		the long value to be hashed
	 */
	public static void update(MessageDigest digest, long n) {
		for (int i = 0; i < Long.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}
	}

	/**
	 * hash the given int
	 *
	 * @param digest
	 * 		the message digest to update
	 * @param n
	 * 		the int value to be hashed
	 */
	public static void update(MessageDigest digest, int n) {
		for (int i = 0; i < Integer.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}
	}

	/**
	 * hash the given array of bytes
	 *
	 * @param digest
	 * 		the message digest to update
	 * @param t
	 * 		the byte array to be hashed
	 * @param offset
	 * 		the offset to start from in the array of bytes
	 * @param length
	 * 		the number of bytes to use, starting at {@code offset}
	 */
	public static void update(MessageDigest digest, byte[] t, int offset, int length) {
		if (t == null) {
			update(digest, 0);
		} else {
			update(digest, t.length);
			digest.update(t, offset, length);
		}
	}


	/**
	 * hash the given Instant
	 *
	 * @param digest
	 * 		the message digest to update
	 * @param i
	 * 		the Instant to be hashed
	 */
	public static void update(MessageDigest digest, Instant i) {
		// the instant class consists of only 2 parts, the seconds and the nanoseconds
		update(digest, i.getEpochSecond());
		update(digest, i.getNano());
	}

	/**
	 * hash the given array of byte arrays
	 * @param digest
	 * 		the message digest to update
	 * @param t
	 * 		the array to be hashed
	 */
	public static void update(MessageDigest digest, byte[][] t) {
		if (t == null) {
			update(digest, 0);
		} else {
			update(digest, t.length);
			for (byte[] a : t) {
				if (a == null) {
					update(digest, 0);
				} else {
					update(digest, a.length);
					digest.update(a);
				}
			}
		}
	}

	/**
	 * hash the given array of bytes
	 * @param digest
	 * 		the message digest to update
	 * @param t
	 * 		the array of bytes to be hashed
	 */
	public static void update(MessageDigest digest, byte[] t) {
		if (t == null) {
			update(digest, 0);
		} else {
			update(digest, t.length);
			digest.update(t);
		}
	}
}

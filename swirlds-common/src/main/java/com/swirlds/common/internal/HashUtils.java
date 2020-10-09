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

package com.swirlds.common.internal;

import com.swirlds.common.Transaction;

import java.security.MessageDigest;
import java.time.Instant;

public abstract class HashUtils {
	/**
	 * hash the given long
	 */
	public static void update(MessageDigest digest, long n) {
		for (int i = 0; i < Long.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}
	}

	/**
	 * hash the given int
	 */
	public static void update(MessageDigest digest, int n) {
		for (int i = 0; i < Integer.BYTES; i++) {
			digest.update((byte) (n & 0xFF));
			n >>= Byte.SIZE;
		}
	}

	/**
	 * hash the given array of bytes
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
	 */
	public static void update(MessageDigest digest, Instant i) {
		// the instant class consists of only 2 parts, the seconds and the nanoseconds
		update(digest, i.getEpochSecond());
		update(digest, i.getNano());
	}

	/**
	 * hash the given array of byte arrays
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
	 */
	public static void update(MessageDigest digest, byte[] t) {
		if (t == null) {
			update(digest, 0);
		} else {
			update(digest, t.length);
			digest.update(t);
		}
	}


	/**
	 * hash the given array of transactions
	 */
	public static void update(MessageDigest digest, Transaction[] t) {
		if (t == null) {
			update(digest, 0);
		} else {
			update(digest, t.length);
			for (Transaction a : t) {
				if (a == null) {
					update(digest, 0);
				} else {
					a.computeDigest(digest);
				}
			}
		}
	}

}

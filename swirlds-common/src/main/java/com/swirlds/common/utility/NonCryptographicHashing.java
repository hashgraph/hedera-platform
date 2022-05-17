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

package com.swirlds.common.utility;

/**
 * <p>
 * This class contains a collection of methods for hashing basic data types.
 * Hashes are not cryptographically secure, and are intended to be used when
 * implementing {@link Object#hashCode()} or similar functionality.
 * </p>
 *
 * <p>
 * This class provides a large number of methods with different signatures, the goal being to avoid the
 * creation of arrays to pass a variable number of arguments. Hashing happens a lot and needs to be fast,
 * so if we can avoid lots of extra allocations it is worthwhile.
 * </p>
 */
public final class NonCryptographicHashing {

	private NonCryptographicHashing() {

	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 1 long.
	 *
	 * @param x0
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0) {

		return (int) hash64(x0);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 1 long.
	 *
	 * @param x0
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0) {

		return perm64(x0);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 2 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1) {
		return (int) hash64(x0, x1);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 2 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1) {
		return perm64(perm64(
				x0) ^ x1);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 3 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2) {
		return (int) hash64(x0, x1, x2);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 3 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2) {
		return perm64(perm64(perm64(
				x0) ^ x1) ^ x2);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 4 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3) {
		return (int) hash64(x0, x1, x2, x3);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 4 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3) {
		return perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 5 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4) {
		return (int) hash64(x0, x1, x2, x3, x4);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 5 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4) {
		return perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 6 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5) {
		return (int) hash64(x0, x1, x2, x3, x4, x5);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 6 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5) {
		return perm64(perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 7 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6) {
		return (int) hash64(x0, x1, x2, x3, x4, x5, x6);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 7 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6) {
		return perm64(perm64(perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 8 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7) {
		return (int) hash64(x0, x1, x2, x3, x4, x5, x6, x7);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 8 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7) {
		return perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 9 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @param x8
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7,
			final long x8) {
		return (int) hash64(x0, x1, x2, x3, x4, x5, x6, x7, x8);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 9 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @param x8
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7,
			final long x8) {
		return perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7) ^ x8);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for 10 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @param x8
	 * 		a long
	 * @param x9
	 * 		a long
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7,
			final long x8,
			final long x9) {
		return (int) hash64(x0, x1, x2, x3, x4, x5, x6, x7, x8, x9);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for 10 longs.
	 *
	 * @param x0
	 * 		a long
	 * @param x1
	 * 		a long
	 * @param x2
	 * 		a long
	 * @param x3
	 * 		a long
	 * @param x4
	 * 		a long
	 * @param x5
	 * 		a long
	 * @param x6
	 * 		a long
	 * @param x7
	 * 		a long
	 * @param x8
	 * 		a long
	 * @param x9
	 * 		a long
	 * @return a non-cryptographic long hash
	 */
	public static long hash64(
			final long x0,
			final long x1,
			final long x2,
			final long x3,
			final long x4,
			final long x5,
			final long x6,
			final long x7,
			final long x8,
			final long x9) {
		return perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
				x0) ^ x1) ^ x2) ^ x3) ^ x4) ^ x5) ^ x6) ^ x7) ^ x8) ^ x9);
	}

	/**
	 * Generates a non-cryptographic 32 bit hash for an array of longs.
	 *
	 * @param x
	 * 		an array of longs
	 * @return a non-cryptographic integer hash
	 */
	public static int hash32(final long... x) {
		return (int) hash64(x);
	}

	/**
	 * Generates a non-cryptographic 64 bit hash for an array of longs.
	 *
	 * @param x
	 * 		an array of longs
	 * @return a non-cryptographic integer hash
	 */
	public static long hash64(final long... x) {
		long t = 0;
		for (final long l : x) {
			t = perm64(t ^ l);
		}
		return t;
	}

	/**
	 * <p>
	 * A permutation (invertible function) on 64 bits.
	 * The constants were found by automated search, to
	 * optimize avalanche. Avalanche means that for a
	 * random number x, flipping bit i of x has about a
	 * 50 percent chance of flipping bit j of perm64(x).
	 * For each possible pair (i,j), this function achieves
	 * a probability between 49.8 and 50.2 percent.
	 * </p>
	 *
	 * <p>
	 * Leemon wrote this, it's magic and does magic things. Like holy molly does
	 * this algorithm resolve some nasty hash collisions for troublesome data sets.
	 * Don't mess with this method.
	 * </p>
	 */
	private static long perm64(long x) {
		// Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
		x += x <<  30;
		x ^= x >>> 27;
		x += x <<  16;
		x ^= x >>> 20;
		x += x <<   5;
		x ^= x >>> 18;
		x += x <<  10;
		x ^= x >>> 24;
		x += x <<  30;
		return x;
	}
}

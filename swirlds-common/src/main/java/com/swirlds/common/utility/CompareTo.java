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

package com.swirlds.common.utility;

/**
 * Contains utility methods for comparing values.
 */
public final class CompareTo {

	private CompareTo() {

	}

	/**
	 * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <A>
	 * 		the type of value a
	 * @param <B>
	 * 		the type of value b
	 * @return a &lt; b
	 */
	public static <A extends Comparable<B>, B> boolean isLessThan(final A a, final B b) {
		return a.compareTo(b) < 0;
	}

	/**
	 * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <A>
	 * 		the type of value a
	 * @param <B>
	 * 		the type of value b
	 * @return a &lt;= b
	 */
	public static <A extends Comparable<B>, B> boolean isLessThanOrEqualTo(final A a, final B b) {
		return a.compareTo(b) <= 0;
	}

	/**
	 * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <A>
	 * 		the type of value a
	 * @param <B>
	 * 		the type of value b
	 * @return a &gt; b
	 */
	public static <A extends Comparable<B>, B> boolean isGreaterThan(final A a, final B b) {
		return a.compareTo(b) > 0;
	}

	/**
	 * Compare two values. Syntactic sugar for {@link Comparable#compareTo(Object)}.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <A>
	 * 		the type of value a
	 * @param <B>
	 * 		the type of value b
	 * @return a &gt;= b
	 */
	public static <A extends Comparable<B>, B> boolean isGreaterThanOrEqualTo(final A a, final B b) {
		return a.compareTo(b) >= 0;
	}

	/**
	 * Return the maximum of two values. If the values are equal, returns the first value.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <T>
	 * 		the type of the values
	 * @return the maximum value, or the first value if equal
	 */
	public static <T extends Comparable<T>> T max(final T a, final T b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	/**
	 * Return the minimum of two values. If the values are equal, returns the first value.
	 *
	 * @param a
	 * 		a value
	 * @param b
	 * 		a value
	 * @param <T>
	 * 		the type of the values
	 * @return the minimum value, or the first value if equal
	 */
	public static <T extends Comparable<T>> T min(final T a, final T b) {
		return a.compareTo(b) <= 0 ? a : b;
	}
}

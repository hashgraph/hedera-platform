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

package com.swirlds.logging;

/**
 * A collection of utilities to assist with logging.
 */
public final class LoggingUtils {

	private LoggingUtils() {

	}

	/**
	 * Choose between a singular and plural word depending on a count.
	 *
	 * @param count
	 * 		the count of objects
	 * @param singular
	 * 		the singular version of the word, e.g. "goose"
	 * @param plural
	 * 		the plural version of the word, e.g. "geese"
	 * @return the correct form of the word given the count
	 */
	public static String plural(final long count, final String singular, final String plural) {
		if (count == 1) {
			return singular;
		}
		return plural;
	}

	/**
	 * Choose between a singular and plural word depending on the count.
	 * For words that can be made plural by adding an "s" to the end.
	 *
	 * @param count
	 * 		the count of objects
	 * @param singular
	 * 		the singular version of the word, e.g. "dog"
	 * @return the correct form of the word given the count
	 */
	public static String plural(final long count, final String singular) {
		if (count == 1) {
			return singular;
		}
		return singular + "s";
	}

}

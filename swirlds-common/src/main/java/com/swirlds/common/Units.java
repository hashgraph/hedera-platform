/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.common;

/**
 * Contains a variety constants useful when converting between units.
 *
 * All constants are in the form "UNIT1_TO_UNIT2" where you multiply by the constant to go from UNIT1 to UNIT2,
 * or divide by the constant to go from UNIT2 to UNIT1.
 *
 */
public final class Units {

	private Units() {

	}

	/**
	 * Multiply by this value for converting seconds to nanoseconds.
	 */
	public static final int SECONDS_TO_NANOSECONDS = 1_000_000_000;

	/**
	 * Multiply by this value for converting nanoseconds to seconds.
	 */
	public static final double NANOSECONDS_TO_SECONDS = 1.0 / SECONDS_TO_NANOSECONDS;

	/**
	 * Multiply by this value for converting seconds to microseconds.
	 */
	public static final int SECONDS_TO_MICROSECONDS = 1_000_000;

	/**
	 * Multiply by this value for converting microseconds to seconds.
	 */
	public static final double MICROSECONDS_TO_SECONDS = 1.0 / SECONDS_TO_MICROSECONDS;

	/**
	 * Multiply by this value for converting seconds to milliseconds.
	 */
	public static final int SECONDS_TO_MILLISECONDS = 1_000;

	/**
	 * Multiply by this value for converting microseconds to seconds.
	 */
	public static final double MILLISECONDS_TO_SECONDS = 1.0 / SECONDS_TO_MILLISECONDS;

	/**
	 * Multiply by this value for converting milliseconds to nanoseconds.
	 */
	public static final int MILLISECONDS_TO_NANOSECONDS = 1_000_000;

	/**
	 * Multiply by this value for converting nanoseconds to milliseconds.
	 */
	public static final double NANOSECONDS_TO_MILLISECONDS = 1.0 / MILLISECONDS_TO_NANOSECONDS;

	/**
	 * Multiply by this value for converting milliseconds to microseconds.
	 */
	public static final int MILLISECONDS_TO_MICROSECONDS = 1_000;

	/**
	 * Multiply by this value for converting microseconds to milliseconds.
	 */
	public static final double MICROSECONDS_TO_MILLISECONDS = 1.0 / MILLISECONDS_TO_MICROSECONDS;

	/**
	 * Multiply by this value for converting microseconds to nanoseconds.
	 */
	public static final int MICROSECONDS_TO_NANOSECONDS = 1_000;

	/**
	 * Multiply by this value for converting nanoseconds to microseconds.
	 */
	public static final double NANOSECONDS_TO_MICROSECONDS = 1.0 / MICROSECONDS_TO_NANOSECONDS;

	/**
	 * Multiply by this value for converting minutes to seconds.
	 */
	public static final int MINUTES_TO_SECONDS = 60;

	/**
	 * Multiply by this value for converting hours to minutes.
	 */
	public static final int HOURS_TO_MINUTES = 60;

	/**
	 * Multiply by this value for converting days to hours.
	 */
	public static final int DAYS_TO_HOURS = 24;

	/**
	 * Multiply by this value for converting weeks to days.
	 */
	public static final int WEEKS_TO_DAYS = 7;

	/**
	 * Multiply by this value for converting bits to bytes.
	 */
	public static final int BITS_TO_BYTES = 8;
}

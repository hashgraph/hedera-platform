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

package com.swirlds.common.io;

import java.io.IOException;

public class InvalidStreamPosition extends IOException {

	/**
	 * Constructs an {@code IOException} with {@code null}
	 * as its error detail message.
	 */
	public InvalidStreamPosition() {
	}

	/**
	 * Constructs an {@code IOException} with the specified detail message.
	 *
	 * @param message
	 * 		The detail message (which is saved for later retrieval
	 * 		by the {@link #getMessage()} method)
	 */
	public InvalidStreamPosition(final String message) {
		super(message);
	}

	/**
	 * Constructs an {@code IOException} with the specified detail message
	 * and cause.
	 *
	 * <p> Note that the detail message associated with {@code cause} is
	 * <i>not</i> automatically incorporated into this exception's detail
	 * message.
	 *
	 * @param message
	 * 		The detail message (which is saved for later retrieval
	 * 		by the {@link #getMessage()} method)
	 * @param cause
	 * 		The cause (which is saved for later retrieval by the
	 *        {@link #getCause()} method).  (A null value is permitted,
	 * 		and indicates that the cause is nonexistent or unknown.)
	 * @since 1.6
	 */
	public InvalidStreamPosition(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs an {@code IOException} with the specified cause and a
	 * detail message of {@code (cause==null ? null : cause.toString())}
	 * (which typically contains the class and detail message of {@code cause}).
	 * This constructor is useful for IO exceptions that are little more
	 * than wrappers for other throwables.
	 *
	 * @param cause
	 * 		The cause (which is saved for later retrieval by the
	 *        {@link #getCause()} method).  (A null value is permitted,
	 * 		and indicates that the cause is nonexistent or unknown.)
	 * @since 1.6
	 */
	public InvalidStreamPosition(final Throwable cause) {
		super(cause);
	}


	public InvalidStreamPosition(final long expectedValue, final long actualValue) {
		super(String.format("Invalid value %d read from the stream, expected %d instead.", actualValue, expectedValue));
	}

	public InvalidStreamPosition(final String markerName, final long expectedValue, final long actualValue) {
		super(String.format("Invalid value %d read from the stream, expected %d (%s) instead.", actualValue,
				expectedValue, markerName));
	}


}

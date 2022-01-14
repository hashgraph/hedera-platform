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

package com.swirlds.common.stream;

/**
 * Thrown when parsing an invalid stream file.
 */
public class InvalidStreamFileException extends Exception {
	/**
	 * Constructs a new exception with the specified detail message and
	 * cause.
	 *
	 * @param message
	 * 		the detail message
	 * @param cause
	 * 		the cause
	 */
	public InvalidStreamFileException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new exception with the specified detail message.  The
	 * cause is not initialized.
	 *
	 * @param message
	 * 		the detail message.
	 */
	public InvalidStreamFileException(final String message) {
		super(message);
	}
}

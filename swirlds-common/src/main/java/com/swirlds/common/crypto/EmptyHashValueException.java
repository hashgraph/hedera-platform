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

package com.swirlds.common.crypto;

import com.swirlds.logging.LogMarker;

/**
 * Exception caused when provided hash value contains all zeros
 */
public class EmptyHashValueException extends CryptographyException {

	private static final String MESSAGE = "Provided hash value contained all zeros, hashes must contain at least one " +
			"non-zero byte";

	public EmptyHashValueException() {
		this(MESSAGE);
	}

	public EmptyHashValueException(final String message) {
		super(message, LogMarker.TESTING_EXCEPTIONS);
	}

	public EmptyHashValueException(final String message, final Throwable cause) {
		super(message, cause, LogMarker.TESTING_EXCEPTIONS);
	}

	public EmptyHashValueException(final Throwable cause) {
		this(MESSAGE, cause);
	}
}

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
 * Exception caused when Invalid algorithm name was provided
 */
public class InvalidDigestTypeException extends CryptographyException {

	private static final String MESSAGE_TEMPLATE = "Invalid algorithm name was provided (%s)";

	public InvalidDigestTypeException(final String algorithmName) {
		super(String.format(MESSAGE_TEMPLATE, algorithmName), LogMarker.TESTING_EXCEPTIONS);
	}

	public InvalidDigestTypeException(final String algorithmName, final Throwable cause,
			final LogMarker logMarker) {
		super(String.format(MESSAGE_TEMPLATE, algorithmName), cause, LogMarker.TESTING_EXCEPTIONS);
	}

}

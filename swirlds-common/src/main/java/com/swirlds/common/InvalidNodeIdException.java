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

package com.swirlds.common;

import com.swirlds.logging.LogMarker;

/**
 * Thrown when the given {@link NodeId} was not found in the {@link AddressBook} or a {@code null} node id was passed by
 * the caller.
 */
public class InvalidNodeIdException extends PlatformException {

	public InvalidNodeIdException() {
		super(LogMarker.EXCEPTION);
	}

	public InvalidNodeIdException(final String message) {
		super(message, LogMarker.EXCEPTION);
	}

	public InvalidNodeIdException(final String message, final Throwable cause) {
		super(message, cause, LogMarker.EXCEPTION);
	}

	public InvalidNodeIdException(final Throwable cause) {
		super(cause, LogMarker.EXCEPTION);
	}

}

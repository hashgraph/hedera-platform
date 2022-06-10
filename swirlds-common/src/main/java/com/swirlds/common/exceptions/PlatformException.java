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

package com.swirlds.common.exceptions;

import com.swirlds.logging.LogMarker;


public class PlatformException extends RuntimeException {

	private final LogMarker logMarker;

	public PlatformException(final LogMarker logMarker) {
		this.logMarker = logMarker;
	}

	public PlatformException(final String message, final LogMarker logMarker) {
		super(message);
		this.logMarker = logMarker;
	}


	public PlatformException(final String message, final Throwable cause, final LogMarker logMarker) {
		super(message, cause);
		this.logMarker = logMarker;
	}

	public PlatformException(final Throwable cause, final LogMarker logMarker) {
		super(cause);
		this.logMarker = logMarker;
	}

	public LogMarker getLogMarkerInfo() {
		return logMarker;
	}
}

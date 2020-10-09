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


public class PlatformException extends RuntimeException {

	private LogMarker logMarker;


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

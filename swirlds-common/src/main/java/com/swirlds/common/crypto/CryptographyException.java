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

import com.swirlds.common.PlatformException;
import com.swirlds.logging.LogMarker;

public class CryptographyException extends PlatformException {

	public CryptographyException(final LogMarker logMarker) {
		super(logMarker);
	}


	public CryptographyException(final String message, final LogMarker logMarker) {
		super(message, logMarker);
	}


	public CryptographyException(final String message, final Throwable cause,
			final LogMarker logMarker) {
		super(message, cause, logMarker);
	}


	public CryptographyException(final Throwable cause, final LogMarker logMarker) {
		super(cause, logMarker);
	}
}

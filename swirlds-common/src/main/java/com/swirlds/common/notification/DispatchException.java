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

package com.swirlds.common.notification;

import com.swirlds.common.PlatformException;
import com.swirlds.logging.LogMarker;

public class DispatchException extends PlatformException {

	/**
	 * {@inheritDoc}
	 */
	public DispatchException(final String message) {
		super(message, LogMarker.EXCEPTION);
	}

	/**
	 * {@inheritDoc}
	 */
	public DispatchException(final String message, final Throwable cause) {
		super(message, cause, LogMarker.EXCEPTION);
	}

	/**
	 * {@inheritDoc}
	 */
	public DispatchException(final Throwable cause) {
		super(cause, LogMarker.EXCEPTION);
	}

}

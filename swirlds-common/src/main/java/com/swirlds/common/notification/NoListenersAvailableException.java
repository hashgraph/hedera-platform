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

package com.swirlds.common.notification;

public class NoListenersAvailableException extends DispatchException {

	private static final String DEFAULT_MESSAGE = "Unable to dispatch when no listeners have been registered";

	/**
	 * {@inheritDoc}
	 */
	public NoListenersAvailableException() {
		super(DEFAULT_MESSAGE);
	}

	/**
	 * {@inheritDoc}
	 */
	public NoListenersAvailableException(final String message) {
		super(message);
	}

	/**
	 * {@inheritDoc}
	 */
	public NoListenersAvailableException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * {@inheritDoc}
	 */
	public NoListenersAvailableException(final Throwable cause) {
		super(cause);
	}

}

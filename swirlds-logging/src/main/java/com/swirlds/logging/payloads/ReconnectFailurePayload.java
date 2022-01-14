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

package com.swirlds.logging.payloads;

/**
 * This payload is logged when a reconnect attempt fails.
 */
public class ReconnectFailurePayload extends AbstractLogPayload {

	public enum CauseOfFailure {
		/**
		 * Reconnect failed due to a socket exception.
		 */
		SOCKET,
		/**
		 * Reconnect failed due to the requested teacher being unwilling.
		 */
		REJECTION,
		/**
		 * Reconnect failed due to an error.
		 */
		ERROR
	}

	private CauseOfFailure causeOfFailure;

	public ReconnectFailurePayload() {

	}

	/**
	 * @param message
	 * 		a human readable message
	 * @param causeOfFailure
	 * 		the reason why the reconnect failed
	 */
	public ReconnectFailurePayload(final String message, final CauseOfFailure causeOfFailure) {
		super(message);
		this.causeOfFailure = causeOfFailure;
	}

	public CauseOfFailure getCauseOfFailure() {
		return causeOfFailure;
	}

	public void setCauseOfFailure(final CauseOfFailure causeOfFailure) {
		this.causeOfFailure = causeOfFailure;
	}
}

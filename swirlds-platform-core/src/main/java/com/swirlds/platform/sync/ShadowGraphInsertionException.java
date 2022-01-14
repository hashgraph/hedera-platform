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

package com.swirlds.platform.sync;

/**
 * An exception thrown by {@link ShadowGraph} when an event cannot be added to the shadow graph.
 */
public class ShadowGraphInsertionException extends Exception {

	private final InsertableStatus status;

	/**
	 * Constructs a new runtime exception with the specified detail message. The cause is not initialized, and may
	 * subsequently be initialized by a call to {@link #initCause}.
	 *
	 * @param message
	 * 		the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
	 * @param status
	 * 		the status of the event insertion
	 */
	public ShadowGraphInsertionException(final String message, final InsertableStatus status) {
		super(message);
		this.status = status;
	}

	/**
	 * The status of the event which prevented its insertion into the shadow graph.
	 *
	 * @return the status
	 */
	public InsertableStatus getStatus() {
		return status;
	}
}

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

package com.swirlds.common.threading;

/**
 * An exception wrapper type thrown by the {@code NodeSynchronizer}
 * implementation during the multi-thread execution of the gossip protocol.
 * The original exception can be retrieved via the {@link Exception#getCause()} method.
 */
public class ParallelExecutionException extends Exception {

	private String callerName = "unknown";
	private int taskNumber = -1;

	public ParallelExecutionException(
			final Throwable cause,
			final String callerName,
			final int taskNumber) {
		super(cause);
		this.callerName = callerName;
		this.taskNumber = taskNumber;
	}

	public ParallelExecutionException(final Throwable cause) {
		super(cause);
	}

	@Override
	public String getMessage() {
		return "caller name: " + callerName + ", task number = " + taskNumber + ": " + super.getMessage();
	}


}

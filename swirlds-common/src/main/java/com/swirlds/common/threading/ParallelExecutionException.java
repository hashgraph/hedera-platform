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

package com.swirlds.common.threading;

import java.time.Instant;

/**
 * An exception thrown by @{@link ParallelExecutor} when one or both of the tasks fails. Since these tasks can fail at
 * different times, a timestamp is added to this exception.
 */
public class ParallelExecutionException extends Exception {
	/**
	 * @param cause
	 * 		the original exception
	 * @param time
	 * 		the time to attach to the message
	 */
	public ParallelExecutionException(final Throwable cause, final Instant time) {
		super("Time thrown: " + time.toString(), cause);
	}

	/**
	 * @param cause
	 * 		the original exception
	 */
	public ParallelExecutionException(final Throwable cause) {
		this(cause, Instant.now());
	}

}

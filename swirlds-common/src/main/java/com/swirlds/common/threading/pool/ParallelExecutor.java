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

package com.swirlds.common.threading.pool;

import com.swirlds.common.threading.ThrowingRunnable;

import java.util.concurrent.Callable;

/**
 * Used for executing tasks in parallel
 */
public interface ParallelExecutor {
	/**
	 * Run two tasks in parallel
	 *
	 * @param task1
	 * 		a task to execute in parallel
	 * @param task2
	 * 		a task to execute in parallel
	 * @throws ParallelExecutionException
	 * 		if anything goes wrong
	 */
	<T> T doParallel(Callable<T> task1, Callable<Void> task2) throws ParallelExecutionException;

	/**
	 * Same as {@link #doParallel(Callable, Callable, Runnable)} but without a return type
	 */
	default void doParallel(
			final ThrowingRunnable task1,
			final ThrowingRunnable task2,
			final Runnable onThrow)
			throws ParallelExecutionException {
		doParallel(task1, (Callable<Void>) task2, onThrow);
	}

	/**
	 * Run two tasks in parallel
	 *
	 * @param task1
	 * 		a task to execute in parallel
	 * @param task2
	 * 		a task to execute in parallel
	 * @param onThrow
	 * 		a task to run if an exception gets thrown
	 * @throws ParallelExecutionException
	 * 		if anything goes wrong
	 */
	default <T> T doParallel(final Callable<T> task1, final Callable<Void> task2, final Runnable onThrow)
			throws ParallelExecutionException {
		throw new UnsupportedOperationException("not implemented");
	}
}

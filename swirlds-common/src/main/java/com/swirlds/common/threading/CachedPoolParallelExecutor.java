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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * An implementation that uses a CachedThreadPool to execute parallel tasks
 */
public class CachedPoolParallelExecutor implements ParallelExecutor {
	/**
	 * The thread pool used by this class.
	 */
	private final ExecutorService threadPool;

	/**
	 * @param name
	 * 		the name given to the threads in the pool
	 */
	public CachedPoolParallelExecutor(final String name) {
		threadPool = Executors
				.newCachedThreadPool(
						new ThreadConfiguration()
								.setThreadName(name)
								.setComponent(name)
								.buildFactory());
	}

	/**
	 * Run two tasks in parallel, the second one in the current thread, and the first in a thread from the
	 * syncThreadPool. This method returns only after both have finished.
	 *
	 * @param task1
	 * 		a task to execute in parallel
	 * @param task2
	 * 		a task to execute in parallel
	 * @throws ParallelExecutionException
	 * 		if either of the invoked tasks throws an exception. if both throw an exception, then the task2 exception
	 * 		will be the cause and the task1 exception will be the suppressed exception
	 */
	@Override
	public <T> T doParallel(
			final Callable<T> task1,
			final Callable<Void> task2)
			throws ParallelExecutionException {

		final Future<T> future1 = threadPool.submit(task1);

		// exception to throw, if any of the tasks throw
		ParallelExecutionException toThrow = null;

		try {
			task2.call();
		} catch (Exception e) {
			toThrow = new ParallelExecutionException(e);
		}

		T result = null;
		try {
			result = future1.get();
		} catch (InterruptedException | ExecutionException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			if (toThrow == null) {
				toThrow = new ParallelExecutionException(e);
			} else {
				// if task2 already threw an exception, we add this one as a suppressed exception
				toThrow.addSuppressed(e);
			}
		}

		// if any of the tasks threw an exception then we throw
		if (toThrow != null) {
			throw toThrow;
		}

		return result;
	}
}

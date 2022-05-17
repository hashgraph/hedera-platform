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
	private static final Runnable NOOP = () -> {
	};
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
	 * Run two tasks in parallel, the first one in the current thread, and the second in a background thread.
	 *
	 * This method returns only after both have finished.
	 *
	 * @param foregroundTask
	 * 		a task to execute in parallel
	 * @param backgroundTask
	 * 		a task to execute in parallel
	 * @param onThrow
	 * 		a cleanup task to be executed if an exception gets thrown. if the foreground task throws an exception, this
	 * 		could be used to stop the background task, but not vice versa
	 * @throws ParallelExecutionException
	 * 		if either of the invoked tasks throws an exception. if both throw an exception, then the foregroundTask
	 * 		exception will be the cause and the backgroundTask exception will be the suppressed exception
	 */
	@Override
	public <T> T doParallel(
			final Callable<T> foregroundTask,
			final Callable<Void> backgroundTask,
			final Runnable onThrow)
			throws ParallelExecutionException {
		final Future<Void> future = threadPool.submit(backgroundTask);

		// exception to throw, if any of the tasks throw
		ParallelExecutionException toThrow = null;

		T result = null;
		try {
			result = foregroundTask.call();
		} catch (Exception e) {
			toThrow = new ParallelExecutionException(e);
			onThrow.run();
		}

		try {
			future.get();
		} catch (InterruptedException | ExecutionException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			if (toThrow == null) {
				toThrow = new ParallelExecutionException(e);
				onThrow.run();
			} else {
				// if foregroundTask already threw an exception, we add this one as a suppressed exception
				toThrow.addSuppressed(e);
			}
		}

		// if any of the tasks threw an exception then we throw
		if (toThrow != null) {
			throw toThrow;
		}

		return result;
	}

	/**
	 * Same as {@link #doParallel(Callable, Callable, Runnable)} where the onThrow task is a no-op
	 */
	@Override
	public <T> T doParallel(
			final Callable<T> foregroundTask,
			final Callable<Void> backgroundTask)
			throws ParallelExecutionException {
		return doParallel(foregroundTask, backgroundTask, NOOP);
	}
}

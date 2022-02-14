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

import com.swirlds.common.futures.ConcurrentFuturePool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * A group of {@link Thread}s designed to support the following paradigm:
 *
 * 1) One or more threads are created to perform a task.
 * 2) Zero or more threads are created by those threads (or other descendant threads) to assist with the task.
 * 3) When the task is finished, all worker threads terminate.
 * 4) If any worker thread throws an exception, all threads stop and the exception is delivered to the calling context.
 */
public class StandardWorkGroup {

	private static final Logger LOG = LogManager.getLogger(StandardWorkGroup.class);

	private static final String DEFAULT_TASK_NAME = "IDLE";

	private final String groupName;
	private final ExecutorService executorService;

	private final ConcurrentFuturePool<Void> futures;

	private volatile boolean hasExceptions;

	private final AtomicBoolean firstException = new AtomicBoolean(true);
	private final Runnable onException;

	/**
	 * Create a new work group.
	 *
	 * @param groupName
	 * 		the name of the group
	 * @param abortAction
	 * 		if an exception is encountered, execute this method.
	 * 		All threads in the work group are interrupted, but
	 * 		if there is additional cleanup required then this method
	 * 		can be used to perform that cleanup. Method is called at most
	 * 		one time. If argument is null then no additional action is taken.
	 */
	public StandardWorkGroup(final String groupName, final Runnable abortAction) {
		this.groupName = groupName;
		this.futures = new ConcurrentFuturePool<>(this::handleError);

		this.onException = abortAction;

		final ThreadConfiguration configuration = new ThreadConfiguration()
				.setComponent("work group " + groupName).setThreadName(DEFAULT_TASK_NAME);

		this.executorService = Executors.newCachedThreadPool(configuration.buildFactory());
	}


	public void shutdown() {
		executorService.shutdown();
	}


	public boolean isShutdown() {
		return executorService.isShutdown();
	}

	public boolean isTerminated() {
		return executorService.isTerminated();
	}


	/**
	 * Perform an action on a thread managed by the work group. Any uncaught exception
	 * (excluding {@link InterruptedException}) will be caught by the work group and will result
	 * in the termination of all threads in the work group.
	 *
	 * @param operation
	 * 		the method to run on the thread
	 */
	@SuppressWarnings("unchecked")
	public void execute(final Runnable operation) {
		futures.add((Future<Void>) executorService.submit(operation));
	}

	/**
	 * Perform an action on a thread managed by the work group. Any uncaught exception
	 * (excluding {@link InterruptedException}) will be caught by the work group and will result
	 * in the termination of all threads in the work group.
	 *
	 * @param taskName
	 * 		used when naming the thread used by the work group
	 * @param operation
	 * 		the method to run on the thread
	 */
	public void execute(final String taskName, final Runnable operation) {
		final Runnable wrapper = () -> {
			final String originalThreadName = Thread.currentThread().getName();
			final String newThreadName = originalThreadName.replaceFirst(DEFAULT_TASK_NAME, taskName);

			try {
				Thread.currentThread().setName(newThreadName);
				operation.run();
			} finally {
				Thread.currentThread().setName(originalThreadName);
			}
		};

		execute(wrapper);
	}

	public boolean hasExceptions() {
		return hasExceptions;
	}

	public void waitForTermination() throws InterruptedException {
		futures.waitForCompletion();
		executorService.shutdown();

		while (!executorService.isTerminated()) {
			if (executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
				break;
			}
		}
	}

	private void handleError(final Throwable ex) {

		if (onException != null && firstException.getAndSet(false)) {
			onException.run();
		}

		if (!(ex instanceof InterruptedException)) {
			LOG.error(EXCEPTION.getMarker(), "Work Group Exception [ groupName = {} ]", groupName, ex);
			hasExceptions = true;
			executorService.shutdownNow();
		}
	}

}

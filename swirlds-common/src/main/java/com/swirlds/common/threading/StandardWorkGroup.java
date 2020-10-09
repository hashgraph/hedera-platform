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

package com.swirlds.common.threading;

import com.swirlds.common.futures.ConcurrentFuturePool;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A group of {@link Thread}s designed to support the following paradigm:
 *
 * 1) One or more threads are created to perform a task.
 * 2) Zero or more threads are created by those threads (or other descendant threads) to assist with the task.
 * 3) When the task is finished, all worker threads terminate.
 * 4) If any worker thread throws an exception, all threads stop and the exception is delivered to the calling context.
 */
public class StandardWorkGroup {

	private static final String POOL_PREFIX_PATTERN = "< work group: %s ";

	private final String groupName;
	private final ExecutorService executorService;

	private volatile ConcurrentFuturePool futures;
	private volatile Queue<Throwable> exceptions;

	public StandardWorkGroup(final String groupName) {
		this.groupName = groupName;
		this.exceptions = new ConcurrentLinkedQueue<>();
		this.futures = new ConcurrentFuturePool<>(this::handleError);

		final ThreadFactory threadFactory = new StandardThreadFactoryBuilder()
				.poolNamePrefixPattern(POOL_PREFIX_PATTERN)
				.poolName(groupName)
				.build();

		this.executorService = Executors.newCachedThreadPool(threadFactory);
	}


	/**
	 * {@inheritDoc}
	 */
	public void shutdown() {
		executorService.shutdown();
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean isShutdown() {
		return executorService.isShutdown();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isTerminated() {
		return executorService.isTerminated();
	}

	/**
	 * {@inheritDoc}
	 */
	public void execute(final Runnable command) {
		futures.add(executorService.submit(command));
	}

	public void execute(final String taskName, final Runnable command) {
		final String threadName = String.format(POOL_PREFIX_PATTERN + "@ task: %s >", groupName, taskName);
		final Runnable wrapper = () -> {
			final String originalThreadName = Thread.currentThread().getName();

			try {
				Thread.currentThread().setName(threadName);
				command.run();
			} finally {
				Thread.currentThread().setName(originalThreadName);
			}
		};

		execute(wrapper);
	}

	public Queue<Throwable> getExceptions() {
		return exceptions;
	}

	public boolean hasExceptions() {
		return !exceptions.isEmpty();
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

	public synchronized void logAllExceptions(final Logger logger, final Marker marker, final Level level) {
		for (final Throwable ex : exceptions) {
			logger.log(level, marker, "Work Group Exception [ groupName = {} ]", groupName, ex);
		}
	}

	private void handleError(final Throwable ex) {
		exceptions.add(ex);
		executorService.shutdownNow();
	}

}

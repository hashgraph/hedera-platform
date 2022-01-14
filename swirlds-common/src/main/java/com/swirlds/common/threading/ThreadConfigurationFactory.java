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

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * A ThreadFactory implementation.
 */
class ThreadConfigurationFactory implements ThreadFactory {

	private final Supplier<String> threadName;
	private final int priority;
	private final ThreadGroup threadGroup;
	private final boolean daemon;
	private final ClassLoader contextClassLoader;
	private final Thread.UncaughtExceptionHandler exceptionHandler;

	/**
	 * This constructor is intentionally package private. This object should always be constructed using a
	 * {@link ThreadConfiguration} object.
	 */
	ThreadConfigurationFactory(
			final Supplier<String> threadName,
			final int priority,
			final ThreadGroup threadGroup,
			final boolean daemon,
			final ClassLoader contextClassLoader,
			final Thread.UncaughtExceptionHandler exceptionHandler) {

		this.threadName = threadName;
		this.priority = priority;
		this.threadGroup = threadGroup;
		this.daemon = daemon;
		this.contextClassLoader = contextClassLoader;
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Thread newThread(final Runnable runnable) {
		final Thread thread = new Thread(threadGroup, runnable);
		thread.setName(threadName.get());
		thread.setPriority(priority);
		thread.setDaemon(daemon);
		thread.setContextClassLoader(contextClassLoader);
		thread.setUncaughtExceptionHandler(exceptionHandler);
		return thread;
	}
}

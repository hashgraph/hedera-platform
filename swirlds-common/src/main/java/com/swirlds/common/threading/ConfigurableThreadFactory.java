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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom {@link ThreadFactory} implementation that supports custom thread names and an option exception handler.
 */
class ConfigurableThreadFactory implements ThreadFactory {

	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final String poolName;
	private final ThreadGroup threadGroup;
	private final boolean daemon;
	private final int priority;
	private final ClassLoader contextClassLoader;
	private final Thread.UncaughtExceptionHandler exceptionHandler;

	/**
	 * Constructor that creates a {@link ThreadFactory} implementation with the given thread name prefix and an optional
	 * exception handler.
	 *
	 * @param poolNamePrefixPattern
	 * 		the prefix pattern to use for {@link Thread#getName()} which conforms to the {@link String#format(String,
	 *        Object...)} syntax where the only available substitution is a single {@link String} value for the {@code
	 * 		poolName} field
	 * @param poolName
	 * 		the prefix to be assigned to each {@link Thread#getName()} created by this factory
	 * @param daemon
	 * 		the value to be set via {@link Thread#setDaemon(boolean)} for each thread created by this factory
	 * @param priority
	 * 		the to be set via {@link Thread#setPriority(int)} for each thread created by this factory
	 * @param contextClassLoader
	 * 		an optional {@link ClassLoader} instance to be set via {@link Thread#setContextClassLoader(ClassLoader)} for
	 * 		each thread created by this factory
	 * @param exceptionHandler
	 * 		an optional exception handler to be assigned to every thread created by this factory
	 */
	ConfigurableThreadFactory(final String poolNamePrefixPattern, final String poolName, final boolean daemon,
			final int priority, final ClassLoader contextClassLoader,
			final Thread.UncaughtExceptionHandler exceptionHandler) {
		SecurityManager s = System.getSecurityManager();
		threadGroup = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();

		this.poolName = String.format(poolNamePrefixPattern, poolName);
		this.daemon = daemon;
		this.priority = priority;
		this.contextClassLoader = contextClassLoader;
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Constructs a new {@code Thread}. Implementations may also initialize priority, name, daemon status,
	 * {@code ThreadGroup}, etc.
	 *
	 * @param r
	 * 		a runnable to be executed by new thread instance
	 * @return constructed thread, or {@code null} if the request to create a thread is rejected
	 */
	@Override
	public Thread newThread(final Runnable r) {
		final Thread t = new Thread(threadGroup, r, poolName + threadNumber.getAndIncrement() + " >", 0);

		t.setDaemon(daemon);
		t.setPriority(priority);

		if (exceptionHandler != null) {
			t.setUncaughtExceptionHandler(exceptionHandler);
		}

		if (contextClassLoader != null) {
			t.setContextClassLoader(contextClassLoader);
		}

		return t;
	}
}

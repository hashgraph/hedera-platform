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

package com.swirlds.common.crypto.engine;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom {@link ThreadFactory} implementation that supports custom thread names and an option exception handler.
 */
public class CryptoThreadFactory implements ThreadFactory {

	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final String poolName;
	private final ThreadGroup threadGroup;
	private final ThreadExceptionHandler exceptionHandler;

	/**
	 * Constructor that creates a {@link ThreadFactory} implementation with the given thread name prefix and an optional
	 * exception handler.
	 *
	 * @param poolName
	 * 		the prefix to be assigned to each {@link Thread#getName()} created by this factory
	 * @param exceptionHandler
	 * 		an optional exception handler to be assigned to every thread created by this factory
	 */
	public CryptoThreadFactory(final String poolName, final ThreadExceptionHandler exceptionHandler) {
		SecurityManager s = System.getSecurityManager();
		threadGroup = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();

		this.poolName = String.format("< adv crypto: %s tp worker ", poolName);
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

		if (!t.isDaemon()) {
			t.setDaemon(true);
		}

		if (t.getPriority() != Thread.NORM_PRIORITY) {
			t.setPriority(Thread.NORM_PRIORITY);
		}

		if (exceptionHandler != null) {
			t.setUncaughtExceptionHandler(exceptionHandler);
		}

		return t;
	}
}

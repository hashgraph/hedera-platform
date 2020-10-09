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

/**
 * Unified builder model for creating and configuring custom {@link ThreadFactory} instances.
 */
public class StandardThreadFactoryBuilder {

	private String poolNamePrefixPattern;
	private String poolName;
	private boolean daemon;
	private int priority;
	private ClassLoader contextClassLoader;
	private Thread.UncaughtExceptionHandler exceptionHandler;

	/**
	 * Constructs a new builder instance with reasonable defaults.
	 *
	 * <p>
	 * initial defaults are as follows:
	 * <ul>
	 * <li>{@code poolNamePrefixPattern = "< platform: %s tp worker "}</li>
	 * <li>{@code poolName = "unknown"}</li>
	 * <li>{@code daemon = true}</li>
	 * <li>{@code priority = Thread.NORM_PRIORITY}</li>
	 * </ul>
	 */
	public StandardThreadFactoryBuilder() {
		this.poolNamePrefixPattern = "< platform: %s tp worker ";
		this.poolName = "unknown";
		this.daemon = true;
		this.priority = Thread.NORM_PRIORITY;
	}

	/**
	 * Gets the configured pool name prefix pattern to use when creating each thread. The pattern accepts one {@link
	 * String} value containing the {@link #getPoolName()} and must conform to the {@link String#format(String,
	 * Object...)} syntax.
	 *
	 * @return the configured pool name prefix pattern
	 */
	public String getPoolNamePrefixPattern() {
		return poolNamePrefixPattern;
	}

	/**
	 * Sets the pool name prefix pattern to use when creating each thread. The pattern accepts one {@link String} value
	 * containing the {@link #getPoolName()} and must conform to the {@link String#format(String, Object...)} syntax.
	 *
	 * @param poolNamePrefixPattern
	 * 		the pattern to use when creating the thread name prefix
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder poolNamePrefixPattern(final String poolNamePrefixPattern) {
		this.poolNamePrefixPattern = poolNamePrefixPattern;
		return this;
	}

	/**
	 * Gets the configured pool name to use when naming each thread. This is used as the value substituted in the {@link
	 * #getPoolNamePrefixPattern()} when generating the thread name prefix.
	 *
	 * @return the configured pool name
	 */
	public String getPoolName() {
		return poolName;
	}

	/**
	 * Sets the pool name to use when naming each thread. This is used as the value substituted in the {@link
	 * #getPoolNamePrefixPattern()} when generating the thread name prefix.
	 *
	 * @param poolName
	 * 		the pool name to be substituted in the {@link #getPoolNamePrefixPattern()}
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder poolName(final String poolName) {
		this.poolName = poolName;
		return this;
	}

	/**
	 * Gets the configured daemon settings which will be set on each thread via {@link Thread#setDaemon(boolean)}.
	 *
	 * @return the configured pool name
	 */
	public boolean isDaemon() {
		return daemon;
	}

	/**
	 * Sets the daemon settings which will be set on each thread via {@link Thread#setDaemon(boolean)}.
	 *
	 * @param daemon
	 * 		flag indicating whether or not the threads should have {@link Thread#setDaemon(boolean)} enabled
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder daemon(final boolean daemon) {
		this.daemon = daemon;
		return this;
	}

	/**
	 * Gets the configured priority settings which will be set on each thread via {@link Thread#setPriority(int)}.
	 *
	 * @return the configured thread priority
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * Sets the priority settings which will be set on each thread via {@link Thread#setPriority(int)}.
	 *
	 * @param priority
	 * 		the thread priority to be configured on the threads via {@link Thread#setPriority(int)}
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder priority(final int priority) {
		this.priority = priority;
		return this;
	}

	/**
	 * Gets the configured context {@link ClassLoader} which will be set on each thread via {@link
	 * Thread#setContextClassLoader(ClassLoader)}.
	 *
	 * @return the configured context {@link ClassLoader}
	 */
	public ClassLoader getContextClassLoader() {
		return contextClassLoader;
	}


	/**
	 * Sets the context {@link ClassLoader} which will be set on each thread via {@link
	 * Thread#setContextClassLoader(ClassLoader)}.
	 *
	 * @param contextClassLoader
	 * 		the thread {@link ClassLoader} to be configured on the threads via
	 *        {@link Thread#setContextClassLoader(ClassLoader)}
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder contextClassLoader(final ClassLoader contextClassLoader) {
		this.contextClassLoader = contextClassLoader;
		return this;
	}

	/**
	 * Gets the configured {@link Thread.UncaughtExceptionHandler} which will be set on each thread via {@link
	 * Thread#setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)}.
	 *
	 * @return the configured {@link Thread.UncaughtExceptionHandler}
	 */
	public Thread.UncaughtExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}

	/**
	 * Sets the {@link Thread.UncaughtExceptionHandler} which will be set on each thread via {@link
	 * Thread#setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)}.
	 *
	 * @param exceptionHandler
	 * 		the {@link Thread.UncaughtExceptionHandler} to be configured on the threads via
	 *        {@link Thread#setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler)}
	 * @return the current builder instance
	 */
	public StandardThreadFactoryBuilder exceptionHandler(final Thread.UncaughtExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
		return this;
	}

	/**
	 * Builds and returns the fully configured {@link ThreadFactory} instance.
	 *
	 * @return the fully configured {@link ThreadFactory} instance
	 */
	public ThreadFactory build() {
		return new ConfigurableThreadFactory(
				poolNamePrefixPattern,
				poolName,
				daemon,
				priority,
				contextClassLoader,
				exceptionHandler
		);
	}
}

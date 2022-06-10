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

package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;

/**
 * Boilerplate getters, setters, and configuration for queue thread pool configuration.
 *
 * @param <C>
 * 		the type of the class extending this class
 * @param <T>
 * 		the type of the objects in the queue
 */
public abstract class AbstractQueueThreadPoolConfiguration<C extends AbstractQueueThreadConfiguration<C, T>, T>
		extends AbstractQueueThreadConfiguration<QueueThreadPoolConfiguration<T>, T> {


	private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

	private int threadCount = DEFAULT_THREAD_COUNT;

	protected AbstractQueueThreadPoolConfiguration() {

	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy
	 */
	protected AbstractQueueThreadPoolConfiguration(final AbstractQueueThreadPoolConfiguration<C, T> that) {
		super(that);

		this.threadCount = that.threadCount;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract AbstractQueueThreadPoolConfiguration<C, T> copy();

	/**
	 * Build a new QueueThreadPool from this configuration.
	 *
	 * @param start
	 * 		if true then automatically start the threads in the pool
	 * @return a QueueThreadPool
	 */
	protected QueueThreadPool<T> buildQueueThreadPool(final boolean start) {
		final QueueThreadPool<T> pool = new QueueThreadPoolImpl<>(this);

		if (start) {
			pool.start();
		}

		return pool;
	}

	/**
	 * Get the number of threads in the pool.
	 *
	 * @return the number of threads in the pool
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 * Set the number of threads in the pool.
	 *
	 * @param threadCount
	 * 		the number of threads in the pool
	 * @return this object
	 */
	@SuppressWarnings("unchecked")
	public C setThreadCount(final int threadCount) {
		throwIfImmutable();
		this.threadCount = threadCount;
		return (C) this;
	}

}

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

package com.swirlds.common.threading.framework.config;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.internal.AbstractQueueThreadPoolConfiguration;

/**
 * Implements a thread pool that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThreadPoolConfiguration<T>
		extends AbstractQueueThreadPoolConfiguration<QueueThreadPoolConfiguration<T>, T> {

	public QueueThreadPoolConfiguration() {
		super();
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy.
	 */
	private QueueThreadPoolConfiguration(final QueueThreadPoolConfiguration<T> that) {
		super(that);
	}

	/**
	 * Get a copy of this configuration. New copy is always mutable,
	 * and the mutability status of the original is unchanged.
	 *
	 * @return a copy of this configuration
	 */
	@Override
	public QueueThreadPoolConfiguration<T> copy() {
		return new QueueThreadPoolConfiguration<>(this);
	}

	/**
	 * <p>
	 * Build a new queue thread pool. Does not start the pool.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * pools.
	 * </p>
	 *
	 * @return a queue thread built using this configuration
	 */
	public QueueThreadPool<T> build() {
		return build(false);
	}

	/**
	 * <p>
	 * Build a new queue thread pool.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * pools.
	 * </p>
	 *
	 * @param start
	 * 		if true then start the pool
	 * @return a queue thread pool built using this configuration
	 */
	public QueueThreadPool<T> build(final boolean start) {
		final QueueThreadPool<T> pool = buildQueueThreadPool(start);
		becomeImmutable();
		return pool;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * This method must be thread safe, as it will be invoked on multiple threads in parallel.
	 * </p>
	 */
	@Override
	public QueueThreadPoolConfiguration<T> setHandler(final InterruptableConsumer<T> handler) {
		return super.setHandler(handler);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InterruptableConsumer<T> getHandler() {
		return super.getHandler();
	}
}

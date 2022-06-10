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
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.internal.AbstractQueueThreadConfiguration;

/**
 * An object used to configure and build {@link QueueThread}s.
 *
 * @param <T>
 * 		the type held by the queue
 */
public class QueueThreadConfiguration<T> extends AbstractQueueThreadConfiguration<QueueThreadConfiguration<T>, T> {

	/**
	 * Build a new queue thread configuration with default values.
	 */
	public QueueThreadConfiguration() {
		super();
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy.
	 */
	private QueueThreadConfiguration(final QueueThreadConfiguration<T> that) {
		super(that);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public QueueThreadConfiguration<T> copy() {
		return new QueueThreadConfiguration<>(this);
	}

	/**
	 * <p>
	 * Build a new queue thread. Does not start the thread.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * threads.
	 * </p>
	 *
	 * @return a queue thread built using this configuration
	 */
	public QueueThread<T> build() {
		return build(false);
	}

	/**
	 * <p>
	 * Build a new queue thread.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * threads.
	 * </p>
	 *
	 * @param start
	 * 		if true then start the thread
	 * @return a queue thread built using this configuration
	 */
	public QueueThread<T> build(final boolean start) {
		final QueueThread<T> queueThread = buildQueueThread(start);
		becomeImmutable();
		return queueThread;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public QueueThreadConfiguration<T> setHandler(final InterruptableConsumer<T> handler) {
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

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

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.TypedStoppableThread;
import com.swirlds.common.threading.framework.internal.AbstractStoppableThreadConfiguration;

/**
 * An object responsible for configuring and constructing {@link StoppableThread}s.
 *
 * @param <T>
 * 		the type of instance that will do work
 */
public class StoppableThreadConfiguration<T extends InterruptableRunnable>
		extends AbstractStoppableThreadConfiguration<StoppableThreadConfiguration<T>, T> {

	/**
	 * Build a new stoppable thread configuration with default values.
	 */
	public StoppableThreadConfiguration() {
		super();
	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the configuration to copy.
	 */
	private StoppableThreadConfiguration(final StoppableThreadConfiguration<T> that) {
		super(that);
	}

	/**
	 * Get a copy of this configuration. New copy is always mutable,
	 * and the mutability status of the original is unchanged.
	 *
	 * @return a copy of this configuration
	 */
	public StoppableThreadConfiguration<T> copy() {
		return new StoppableThreadConfiguration<>(this);
	}

	/**
	 * <p>
	 * Build a new thread. Do not start it automatically.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * threads.
	 * </p>
	 *
	 * @return a stoppable thread built using this configuration
	 */
	public TypedStoppableThread<T> build() {
		return build(false);
	}

	/**
	 * <p>
	 * Build a new thread.
	 * </p>
	 *
	 * <p>
	 * After calling this method, this configuration object should not be modified or used to construct other
	 * threads.
	 * </p>
	 *
	 * @param start
	 * 		if true then start the thread before returning it
	 * @return a stoppable thread built using this configuration
	 */
	public TypedStoppableThread<T> build(final boolean start) {
		final TypedStoppableThread<T> thread = buildStoppableThread(start);
		becomeImmutable();
		return thread;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T getWork() {
		return super.getWork();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StoppableThreadConfiguration<T> setWork(final T work) {
		return super.setWork(work);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InterruptableRunnable getFinalCycleWork() {
		return super.getFinalCycleWork();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StoppableThreadConfiguration<T> setFinalCycleWork(final InterruptableRunnable finalCycleWork) {
		return super.setFinalCycleWork(finalCycleWork);
	}
}

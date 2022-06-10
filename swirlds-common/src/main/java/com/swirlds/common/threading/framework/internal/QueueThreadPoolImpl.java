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

import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.QueueThreadPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a thread pool that continuously takes elements from a queue and handles them.
 *
 * @param <T>
 * 		the type of the item in the queue
 */
public class QueueThreadPoolImpl<T> extends AbstractBlockingQueue<T> implements QueueThreadPool<T> {

	private final List<QueueThread<T>> threads = new ArrayList<>();

	/**
	 * Build a new thread queue pool.
	 *
	 * @param configuration
	 * 		configuration for the thread pool
	 */
	protected QueueThreadPoolImpl(final AbstractQueueThreadPoolConfiguration<?, T> configuration) {
		super(configuration.getOrInitializeQueue());

		configuration.enableThreadNumbering();

		for (int threadIndex = 0; threadIndex < configuration.getThreadCount(); threadIndex++) {
			threads.add(configuration.copy().buildQueueThread(false));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ThreadSeed> buildSeeds() {
		final List<ThreadSeed> seeds = new ArrayList<>(threads.size());
		for (final QueueThread<T> thread : threads) {
			seeds.add(thread.buildSeed());
		}
		return seeds;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean start() {
		boolean success = true;
		for (final QueueThread<T> thread : threads) {
			success &= thread.start();
		}
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean stop() {
		boolean success = true;
		for (final QueueThread<T> thread : threads) {
			success &= thread.stop();
		}
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean pause() {
		boolean success = true;
		for (final QueueThread<T> thread : threads) {
			success &= thread.pause();
		}
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean resume() {
		boolean success = true;
		for (final QueueThread<T> thread : threads) {
			success &= thread.resume();
		}
		return success;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join() throws InterruptedException {
		for (final QueueThread<T> thread : threads) {
			thread.join();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis) throws InterruptedException {
		for (final QueueThread<T> thread : threads) {
			thread.join(millis);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void join(final long millis, final int nanos) throws InterruptedException {
		for (final QueueThread<T> thread : threads) {
			thread.join(millis, nanos);
		}
	}
}

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

package com.swirlds.common.threading.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A standard lock that provides the {@link AutoCloseable} semantics. Lock is reentrant.
 */
public class AutoLock implements AutoClosableLock {

	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * Create a new auto lock.
	 */
	public AutoLock() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Locked lock() {
		lock.lock();
		return lock::unlock;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Locked lockInterruptibly() throws InterruptedException {
		lock.lockInterruptibly();
		return lock::unlock;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLocked tryLock() {
		final boolean locked = lock.tryLock();
		return new MaybeLocked() {
			@Override
			public boolean isLockAcquired() {
				return locked;
			}

			@Override
			public void close() {
				if (locked) {
					lock.unlock();
				}
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLocked tryLock(final long time, final TimeUnit unit) throws InterruptedException {
		final boolean locked = lock.tryLock(time, unit);
		return new MaybeLocked() {
			@Override
			public boolean isLockAcquired() {
				return locked;
			}

			@Override
			public void close() {
				if (locked) {
					lock.unlock();
				}
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Condition newCondition() {
		return lock.newCondition();
	}
}

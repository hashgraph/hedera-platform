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
import java.util.concurrent.locks.Lock;

/**
 * An implementation of {@link AutoClosableLock} which holds a resource that needs to be locked before it can be used
 *
 * @param <T>
 * 		the type of resource
 */
public class ResourceLock<T> implements AutoClosableLock {
	private final Lock lock;
	private final MaybeLockedResource<T> acquired;
	private final MaybeLockedResource<T> notAcquired;

	public ResourceLock(final Lock lock, final T resource) {
		this.lock = lock;
		acquired = new AcquiredResource<>(lock::unlock, resource);
		notAcquired = new NotAcquiredResource<>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LockedResource<T> lock() {
		lock.lock();
		return acquired;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public LockedResource<T> lockInterruptibly() throws InterruptedException {
		lock.lockInterruptibly();
		return acquired;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLockedResource<T> tryLock() {
		if (lock.tryLock()) {
			return acquired;
		}
		return notAcquired;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLockedResource<T> tryLock(final long time, final TimeUnit unit) throws InterruptedException {
		if (lock.tryLock(time, unit)) {
			return acquired;
		}
		return notAcquired;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Condition newCondition() {
		return lock.newCondition();
	}
}

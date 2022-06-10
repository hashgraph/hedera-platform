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

/**
 * Has similar semantics to {@link AutoLock}, except that it doesn't actually lock anything.
 */
public final class AutoNoOpLock implements AutoClosableLock {

	private static final Locked locked = () -> {
		// intentional no-op
	};

	private static final MaybeLocked maybeLocked = new MaybeLocked() {
		@Override
		public boolean isLockAcquired() {
			return true;
		}

		@Override
		public void close() {
			// intentional no-op
		}
	};

	private static final AutoNoOpLock instance = new AutoNoOpLock();

	/**
	 * Get an instance of a no-op auto lock. A no-op lock doesn't have any state, so we can reuse the
	 * same one each time.
	 *
	 * @return an instance of a no-op auto-lock
	 */
	public static AutoNoOpLock getInstance() {
		return instance;
	}

	/**
	 * Intentionally private. Use {@link #getInstance()} to get an instance.
	 */
	private AutoNoOpLock() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Locked lock() {
		return locked;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Locked lockInterruptibly() throws InterruptedException {
		return locked;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLocked tryLock() {
		return maybeLocked;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MaybeLocked tryLock(final long time, final TimeUnit unit) throws InterruptedException {
		return maybeLocked;
	}

	/**
	 * Unsupported.
	 *
	 * @throws UnsupportedOperationException
	 * 		if called
	 */
	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException();
	}
}

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

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

package com.swirlds.common.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Similar to {@link java.util.concurrent.locks.Lock} but intended to be used by the try-with-resources statement.
 * Returns an object that will release the lock automatically at the end of the try block.
 */
public interface AutoClosableLock {

	/**
	 * Acquires the lock, blocking until the lock becomes available.
	 *
	 * @return an instance used to release the lock
	 */
	Locked lock();

	/**
	 * Same as {@link #lock()}, but can unblock if interrupted
	 */
	Locked lockInterruptibly() throws InterruptedException;

	/**
	 * Tries to acquire the lock if it is available. Returns immediately
	 *
	 * @return an instance that tells the caller if the lock has been acquired and provides a method to unlock it if it
	 * 		has
	 */
	MaybeLocked tryLock();

	/**
	 * {@link #tryLock()} but with a timeout
	 */
	MaybeLocked tryLock(long time, TimeUnit unit) throws InterruptedException;

	/**
	 * Returns a new {@link Condition} instance that is bound to this
	 * {@code Lock} instance.
	 *
	 * <p>Before waiting on the condition the lock must be held by the
	 * current thread.
	 * A call to {@link Condition#await()} will atomically release the lock
	 * before waiting and re-acquire the lock before the wait returns.
	 *
	 * @return A new {@link Condition} instance for this {@code Lock} instance
	 */
	Condition newCondition();
}

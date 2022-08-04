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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * This class provides a useful abstraction of locking on an index value. That is, two threads could lock on the
 * primitive value "17" and will contend for the same lock.
 * </p>
 *
 * <p>
 * It is possible that a lock on two different indices will contend for the same lock. That probability
 * can be reduced by increasing the parallelism, at the cost of additional memory overhead.
 * </p>
 *
 * <p>
 * The locks acquired by this class are reentrant.
 * </p>
 */
public class IndexLock {

	private final int parallelism;
	private final Lock[] locks;

	/**
	 * Create a new lock for index values.
	 *
	 * @param parallelism
	 * 		the number of unique locks. Higher parallelism reduces chances of collision for non-identical
	 * 		indexes at the cost of additional memory overhead.
	 */
	public IndexLock(final int parallelism) {

		this.parallelism = parallelism;

		this.locks = new Lock[parallelism];
		for (int lockIndex = 0; lockIndex < parallelism; lockIndex++) {
			locks[lockIndex] = new ReentrantLock();
		}
	}

	/**
	 * Lock on a given index value. May contend for the same lock as other index values.
	 *
	 * @param index
	 * 		the value to lock
	 */
	public void lock(final long index) {
		locks[(int) (Math.abs(index) % parallelism)].lock();
	}

	/**
	 * Lock using the hash code of an object as the index. Two objects with the same hash code will contend
	 * for the same lock.
	 *
	 * @param object
	 * 		the object to lock, can be null
	 */
	public void lock(final Object object) {
		final int hash = object == null ? 0 : object.hashCode();
		lock(hash);
	}

	/**
	 * Unlock on a given index value.
	 *
	 * @param index
	 * 		the value to unlock
	 */
	public void unlock(final long index) {
		locks[(int) (Math.abs(index) % parallelism)].unlock();
	}

	/**
	 * Unlock using the hash code of an object as the index. Two objects with the same hash code will contend
	 * for the same lock.
	 *
	 * @param object
	 * 		the object to unlock, can be null
	 */
	public void unlock(final Object object) {
		final int hash = object == null ? 0 : object.hashCode();
		unlock(hash);
	}

	/**
	 * Acquire a lock and return an autocloseable object that will release the lock.
	 *
	 * @param index
	 * 		the index to lock
	 * @return an object that will unlock the lock once it is closed
	 */
	public Locked autoLock(final long index) {
		lock(index);
		return () -> unlock(index);
	}

	/**
	 * Acquire a lock and return an autocloseable object that will release the lock. Uses the hash
	 * code of the provided object.
	 *
	 * @param object
	 * 		the object to lock, can be null
	 * @return an object that will unlock the lock once it is closed
	 */
	public Locked autoLock(final Object object) {
		final int hash = object == null ? 0 : object.hashCode();
		return autoLock(hash);
	}
}

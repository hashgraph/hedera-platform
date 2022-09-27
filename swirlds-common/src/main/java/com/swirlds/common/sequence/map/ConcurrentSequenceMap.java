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

package com.swirlds.common.sequence.map;

import com.swirlds.common.sequence.map.internal.AbstractSequenceMap;
import com.swirlds.common.threading.locks.IndexLock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToLongFunction;

/**
 * A thread safe implementation of {@link SequenceMap}.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class ConcurrentSequenceMap<K, V> extends AbstractSequenceMap<K, V> {

	private static final int DEFAULT_PARALLELISM = 1024;

	private final AtomicLong lowestAllowedSequenceNumber;
	private final AtomicLong highestAllowedSequenceNumber;

	/**
	 * When inserting data into this data structure, it is critical that data is not inserted after the
	 * data's sequence number has been purged (as this would lead to a memory leak). Whenever new data is inserted,
	 * acquire a lock that prevents concurrent purging of that sequence number.
	 */
	private final IndexLock lock = new IndexLock(DEFAULT_PARALLELISM);
	private final Lock windowLock = new ReentrantLock();

	/**
	 * Construct a thread safe {@link SequenceMap}.
	 *
	 * @param getSequenceNumberFromKey
	 * 		a method that extracts the sequence number from a key
	 */
	public ConcurrentSequenceMap(final ToLongFunction<K> getSequenceNumberFromKey) {
		this(DEFAULT_LOWEST_ALLOWED_SEQUENCE_NUMBER, DEFAULT_HIGHEST_ALLOWED_SEQUENCE_NUMBER, getSequenceNumberFromKey);
	}

	/**
	 * Construct a thread safe {@link SequenceMap}.
	 *
	 * @param lowestAllowedSequenceNumber
	 * 		the lowest allowed sequence number
	 * @param highestAllowedSequenceNumber
	 * 		the highest allowed sequence number
	 * @param getSequenceNumberFromKey
	 * 		a method that extracts the sequence number from a key
	 */
	public ConcurrentSequenceMap(
			final long lowestAllowedSequenceNumber,
			final long highestAllowedSequenceNumber,
			final ToLongFunction<K> getSequenceNumberFromKey) {

		super(lowestAllowedSequenceNumber, highestAllowedSequenceNumber, getSequenceNumberFromKey);
		this.lowestAllowedSequenceNumber = new AtomicLong(lowestAllowedSequenceNumber);
		this.highestAllowedSequenceNumber = new AtomicLong(highestAllowedSequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void lockSequenceNumber(final long sequenceNumber) {
		lock.lock(sequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void unlockSequenceNumber(final long sequenceNumber) {
		lock.unlock(sequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void fullLock() {
		lock.fullyLock();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void fullUnlock() {
		lock.fullyUnlock();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void windowLock() {
		windowLock.lock();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void windowUnlock() {
		windowLock.unlock();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLowestAllowedSequenceNumber() {
		return lowestAllowedSequenceNumber.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getHighestAllowedSequenceNumber() {
		return highestAllowedSequenceNumber.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setLowestAllowedSequenceNumber(final long sequenceNumber) {
		lowestAllowedSequenceNumber.set(sequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setHighestAllowedSequenceNumber(final long sequenceNumber) {
		highestAllowedSequenceNumber.set(sequenceNumber);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Map<K, V> buildDataMap() {
		return new ConcurrentHashMap<>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Map<Long, Set<K>> buildSequenceMap() {
		return new ConcurrentHashMap<>();
	}

}

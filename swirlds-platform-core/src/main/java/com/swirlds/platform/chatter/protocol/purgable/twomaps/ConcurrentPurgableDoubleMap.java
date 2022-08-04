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

package com.swirlds.platform.chatter.protocol.purgable.twomaps;

import com.swirlds.common.threading.locks.IndexLock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A thread safe map-like object that allows generation data to be efficiently purged.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class ConcurrentPurgableDoubleMap<K, V> extends AbstractPurgableDoubleMap<K, V> {
	private static final int DEFAULT_PARALLELISM = 1024;
	private final AtomicLong smallestAllowedGeneration = new AtomicLong(0);

	/**
	 * When inserting data into this data structure, it is critical that data is not inserted after the
	 * data's generation has been purged (as this would lead to a memory leak). Whenever new data is inserted,
	 * acquire a lock that prevents concurrent purging of that generation.
	 */
	private final IndexLock lock = new IndexLock(DEFAULT_PARALLELISM);

	/**
	 * Construct a thread safe purgable map.
	 *
	 * @param function
	 * 		a method that extracts a generation from a key
	 */
	public ConcurrentPurgableDoubleMap(final Function<K, Long> function) {
		super(function);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void lockGeneration(final long generation) {
		lock.lock(generation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void unlockGeneration(final long generation) {
		lock.unlock(generation);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getPurgedGeneration() {
		return smallestAllowedGeneration.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void setSmallestAllowedGeneration(final long generation) {
		smallestAllowedGeneration.set(generation);
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
	protected Map<Long, Set<K>> buildGenerationMap() {
		return new ConcurrentHashMap<>();
	}

}

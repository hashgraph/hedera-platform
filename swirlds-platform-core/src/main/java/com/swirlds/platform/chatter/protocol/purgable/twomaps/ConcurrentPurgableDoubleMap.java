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

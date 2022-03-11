/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.virtualmap.internal.hash;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A {@link HashingQueue} implementation based on an array. This implementation is the main data structure
 * used for holding {@link HashJob}s in the {@link VirtualHasher} implementation. An important performance
 * tradeoff that was explicitly made in this class is to favor low-garbage generate over memory usage.
 *
 * @param <K>
 *     The key.
 * @param <V>
 *     The value.
 */
final class ArrayHashingQueue<K extends VirtualKey<? super K>, V extends VirtualValue> implements HashingQueue<K, V> {
	private final AtomicInteger size = new AtomicInteger();
	@SuppressWarnings("unchecked")
	private HashJob<K, V>[] queue = new HashJob[0];
	private int capacity = 0;

	// Loses all data but sizes correctly. Could use multiple queue's like Jasper does elsewhere so these
	// queues are more adaptive to usage.
	void ensureCapacity(int capacity) {
		if (queue.length < capacity) {
			//noinspection unchecked
			queue = new HashJob[capacity];
		}
		this.capacity = capacity;
		reset();
	}

	@Override
	public int size() {
		return size.get();
	}

	@Override
	public HashingQueue<K, V> reset() {
		size.set(0);
		return this;
	}

	@Override
	public HashJob<K, V> appendHashJob() {
		return getForModify(size.getAndIncrement());
	}

	@Override
	public HashJob<K, V> addHashJob(int index) {
		size.updateAndGet(s -> s <= index ? (index + 1) : s);
		return getForModify(index);
	}

	@Override
	public HashJob<K, V> get(int index) {
		assert index < size.get() : "Unexpected index out of bounds " + index;
		return queue[index];
	}

	// Resets the darn thing. Lazily creates it.
	HashJob<K, V> getForModify(int index) {
		assert index < size.get() : "Unexpected index out of bounds " + index;
		assert index < capacity : "Unexpected index out of bounds of capacity " + index;
		HashJob<K, V> job = queue[index];
		if (job == null) {
			job = new HashJob<>();
			queue[index] = job;
		} else {
			job.reset();
		}
		return job;
	}

	@Override
	public Stream<HashJob<K, V>> stream() {
		return Arrays.stream(queue, 0, size());
	}
}

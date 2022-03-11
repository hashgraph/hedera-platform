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

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link HashingQueue} implementation that is backed by (and delegates to) two other
 * queues. There is one specific case in the {@link VirtualHasher} where this is useful
 * and avoids array copies.
 * <p>
 * This implementation is peculiar in that all add modifications ({@link #addHashJob(int)},
 * {@link #appendHashJob()}) only make use of queue1. In our use case, this works out because
 * both queue1 and queue2 are sized the same, and both are individually large enough to contain
 * all data in the "stop level" rank and therefore also large enough for all ranks from there to
 * the root rank. Thus, we can cheat on the implementation. And we do.
 *
 * @param <K>
 *     The key
 * @param <V>
 *     The value
 */
final class CompoundHashingQueue<K extends VirtualKey<? super K>, V extends VirtualValue>
		implements HashingQueue<K, V> {
	private final HashingQueue<K, V> queue1;
	private final HashingQueue<K, V> queue2;

	CompoundHashingQueue(final HashingQueue<K, V> q1, final HashingQueue<K, V> q2) {
		this.queue1 = Objects.requireNonNull(q1);
		this.queue2 = Objects.requireNonNull(q2);
	}

	@Override
	public int size() {
		return queue1.size() + queue2.size();
	}

	@Override
	public HashingQueue<K, V> reset() {
		queue1.reset();
		queue2.reset();
		return this;
	}

	@Override
	public HashJob<K, V> appendHashJob() {
		return queue1.appendHashJob();
	}

	@Override
	public HashJob<K, V> addHashJob(int index) {
		return queue1.addHashJob(index);
	}

	@Override
	public HashJob<K, V> get(int index) {
		final int size1 = queue1.size();
		return (index < size1) ? queue1.get(index) : queue2.get(index - size1);
	}

	@Override
	public Stream<HashJob<K, V>> stream() {
		return Stream.concat(queue1.stream(), queue2.stream());
	}
}


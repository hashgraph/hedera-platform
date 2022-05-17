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

package com.swirlds.platform.eventhandling;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A concurrent queue that keeps round generation information for rounds that have had fame decided.
 */
public class MinGenQueue {
	/** A deque used by doCons to store the minimum generation of famous witnesses per round */
	private final Deque<Pair<Long, Long>> queue = new ConcurrentLinkedDeque<>();

	/**
	 * Add round generation information
	 *
	 * @param round
	 * 		the round
	 * @param minGeneration
	 * 		the minimum generation of all famous witnesses for that round
	 */
	public void add(final long round, final long minGeneration) {
		add(Pair.of(round, minGeneration));
	}

	/**
	 * Same as {@link #add(long, long)}, where the key is the round and the value is the generation
	 *
	 * @param pair
	 * 		a round/generation pair
	 */
	public void add(final Pair<Long, Long> pair) {
		queue.add(pair);
	}

	/**
	 * Get an ordered list of all round/generation pairs up to and including maxRound
	 *
	 * @param maxRound
	 * 		the maximum round included in the list
	 * @return round/generation pairs
	 */
	public List<Pair<Long, Long>> getList(final long maxRound) {
		final List<Pair<Long, Long>> list = new ArrayList<>();
		for (final Pair<Long, Long> next : queue) {
			if (next.getKey() <= maxRound) {
				list.add(next);
			} else {
				break;
			}
		}
		return list;
	}

	/**
	 * Return the generation for the specified round
	 *
	 * @param round
	 * 		the round queried
	 * @return the round generation
	 * @throws NoSuchElementException
	 * 		if the round is not in the queue
	 */
	public long getRoundGeneration(final long round) {
		for (Pair<Long, Long> pair : queue) {
			if (pair.getKey() == round) {
				return pair.getValue();
			}
		}
		throw new NoSuchElementException("Missing generation for round " + round);
	}

	/**
	 * Remove all rounds smaller then the one specified
	 *
	 * @param round
	 * 		the highest round to keep
	 */
	public void expire(final long round) {
		Pair<Long, Long> minGenInfo = queue.peekFirst();

		// remove old min gen info we no longer need
		while (!queue.isEmpty() && minGenInfo.getKey() < round) {
			queue.pollFirst();
			minGenInfo = queue.peekFirst();
		}
	}

	/**
	 * Same as {@link #add(Pair)} but for a whole collection
	 *
	 * @param collection
	 * 		the collection to add
	 */
	public void addAll(final Collection<Pair<Long, Long>> collection) {
		queue.addAll(collection);
	}

	/**
	 * Clear all the data
	 */
	public void clear() {
		queue.clear();
	}
}

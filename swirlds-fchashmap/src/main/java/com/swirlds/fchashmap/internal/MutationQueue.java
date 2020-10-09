/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.fchashmap.internal;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Represents a sequence of mutations on a single data value.
 */
public class MutationQueue<V> extends ConcurrentLinkedDeque<Mutation<V>> {

	/**
	 * When the garbage collector removes this queue from the map it marks it as deleted. A writer must check
	 * to see if the queue has been deleted before writing to it.
	 */
	private boolean deleted;

	public MutationQueue() {
		super();
		deleted = false;
	}

	/**
	 * Add a new mutation to the queue. If the version of this mutation matches the version of the most recent mutation
	 * then simply update the most recent mutation.
	 */
	public void maybeAddLast(Mutation<V> mutation) {
		Mutation<V> last = peekLast();
		if (last != null) {
			if (last.version == mutation.version) {
				// If we are modifying a value that has already been mutated in this version then
				// we can simply update the existing mutation.
				last.value = mutation.value;
				last.deleted = mutation.deleted;
				return;
			}
		}

		addLast(mutation);
	}

	public void delete() {
		deleted = true;
	}

	/**
	 * @return
	 */
	public boolean isDeleted() {
		return deleted;
	}
}

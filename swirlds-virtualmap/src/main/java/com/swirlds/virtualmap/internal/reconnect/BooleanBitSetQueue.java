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

package com.swirlds.virtualmap.internal.reconnect;

import java.util.BitSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * A simple and fast implementation of a boolean queue, backed by one or more {@link BitSet}s. This queue can
 * handle a tremendous number of elements, even far exceeding {@link Long#MAX_VALUE}. You'll run out of RAM first.
 * This is not a thread-safe data structure.
 */
class BooleanBitSetQueue {
	/**
	 * The number of bits in each {@link BitSet}.
	 */
	private final int bitsPerSet;

	/**
	 * A {@link LinkedList} of {@link BitSet}s. The older bitsets are removed from the front of the list
	 * after they have been fully read, and newly added bitsets are added to the end of the queue when they
	 * are created. This is a backlog of bit sets that the reader has yet to get to.
	 */
	private final LinkedList<BitSet> writeBacklog;

	/**
	 * The active {@link BitSet} used for writing. Once it is filled up, a new one is created.
	 */
	private BitSet writeBitSet;

	/**
	 * The active {@link BitSet} used for reading. Once it is fully read, a new one is removed from
	 * the head of {@link #writeBacklog} and used for reading.
	 */
	private BitSet readBitSet;

	/**
	 * The current index within {@link #writeBitSet} into which we will write the next boolean value.
	 * When a new {@link #writeBitSet} is created, this is reset to 0.
	 */
	private int writeIndex = 0;

	/**
	 * The current index within {@link #readBitSet} from which we will read the next boolean value.
	 * When a new {@link #readBitSet} is created, this is reset to 0.
	 */
	private int readIndex = 0;

	/**
	 * Create a new {@link BooleanBitSetQueue}.
	 *
	 * @param capacity
	 * 		The minimum amount of memory to set aside, and the amount by which to increment when the
	 * 		queue needs to be expanded. This must be a positive value.
	 */
	BooleanBitSetQueue(final int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("initialCapacity must be positive");
		}

		this.bitsPerSet = capacity;
		this.writeBacklog = new LinkedList<>();
		this.writeBitSet = new BitSet(bitsPerSet);
		this.readBitSet = writeBitSet;
	}

	/**
	 * Adds the given boolean value to the queue.
	 *
	 * @param value
	 * 		The value to add
	 */
	void add(final boolean value) {
		if (writeIndex >= bitsPerSet) {
			writeBitSet = new BitSet(bitsPerSet);
			writeBacklog.add(writeBitSet);
			writeIndex = 0;
		}
		writeBitSet.set(writeIndex++, value);
	}

	/**
	 * Removes the next boolean value from the head of the queue.
	 *
	 * @return
	 * 		the next boolean value
	 * @throws
	 * 		NoSuchElementException if the queue is empty
	 */
	boolean remove() {
		if (isEmpty()) {
			throw new NoSuchElementException("Cannot remove from an empty queue");
		}

		if (readIndex >= bitsPerSet) {
			// What happens if remove returns null? It really shouldn't be able to reach this code in that condition...
			readBitSet = writeBacklog.remove();
			readIndex = 0;
		}

		return readBitSet.get(readIndex++);
	}

	/**
	 * Gets whether the queue is empty.
	 *
	 * @return
	 * 		{@code true} if the queue is empty.
	 */
	boolean isEmpty() {
		return writeBitSet == readBitSet && readIndex == writeIndex;
	}
}

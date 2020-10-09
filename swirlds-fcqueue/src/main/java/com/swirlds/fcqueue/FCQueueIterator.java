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

package com.swirlds.fcqueue;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator for FCQueue, starts at the head of the given queue, ends at the tail of the given queue
 *
 * @param <E>
 * 		the type of elements in the FCQueue
 */
public class FCQueueIterator<E extends FCQueueElement<E>> implements Iterator<E> {
	/** the node whose element should be returned the next time this.next() is called */
	private FCQueueNode<E> current;

	/** the tail of this queue, and so the last node that this iterator will return */
	private FCQueueNode<E> tail;

	/** the queue that this is iterating over.  This is used to fail-fast when it changes during iteration. */
	private final FCQueue<E> queue;

	/**
	 * the number of times the queue has changed, as of the moment this iterator was created. Fail fast if it
	 * changes
	 */
	private final int numChanges;

	/**
	 * start this iterator at the head of the given queue
	 *
	 * @param queue
	 * 		the queue to iterate over
	 * @param head
	 * 		the head of the queue
	 * @param tail
	 * 		the tail of the queue
	 */
	FCQueueIterator(final FCQueue<E> queue, final FCQueueNode<E> head, final FCQueueNode<E> tail) {
		this.queue = queue;
		this.numChanges = queue.getNumChanges();
		this.current = head;
		this.tail = tail;
	}

	/**
	 * Returns {@code true} if the iteration has more elements.
	 * (In other words, returns {@code true} if {@link #next} would
	 * return an element rather than throwing an exception.)
	 *
	 * @return {@code true} if the iteration has more elements
	 */
	@Override
	public boolean hasNext() {
		return current != null;
	}

	/**
	 * Returns the next element in the iteration.
	 *
	 * @return the next element in the iteration
	 * @throws NoSuchElementException
	 * 		if the iteration has no more elements
	 * @throws ConcurrentModificationException
	 * 		if the queue changes (such as with add, remove, clear, delete) since this iterator was created. This isn't
	 * 		guaranteed to be thrown in that case, but it makes a best effort, so this should only be used for catching
	 * 		bugs, but shouldn't be relied on.
	 */
	@Override
	public E next() throws ConcurrentModificationException {
		final FCQueueNode<E> old = current;

		if (current == null) {
			throw new NoSuchElementException();
		}

		if (numChanges != queue.getNumChanges()) {
			throw new ConcurrentModificationException();
		}

		if (current == tail) {
			current = null;
		} else {
			current = current.towardTail;
		}

		return old.element;
	}

//ConcurrentModificationException

	/**
	 * This always throws an UnsupportedOperationException, because an FCQueue is not designed to allow removal of any
	 * elements other than the head. That is necessary to make fast copies to be fast.
	 *
	 * @throws UnsupportedOperationException
	 * 		if the {@code remove}
	 * 		operation is not supported by this iterator
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException("FCQueue elements can only be removed at the head");
	}
}

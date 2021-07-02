/*
 * (c) 2016-2021 Swirlds, Inc.
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

package com.swirlds.fcqueue.internal;

import com.swirlds.fcqueue.FCQueue;
import com.swirlds.fcqueue.FCQueueElement;

/**
 * An iterator for FCQueue, starts at the tail of the given queue, ends at the head of the given queue
 *
 * @param <E>
 * 		the type of elements in the FCQueue
 */
public final class FCQueueNodeBackwardIterator<E extends FCQueueElement> extends FCQueueNodeIterator<E> {

	/**
	 * start this iterator at the tail of the given queue
	 *
	 * @param queue
	 * 		the queue to iterate over
	 * @param head
	 * 		the head of the queue
	 * @param tail
	 * 		the tail of the queue
	 */
	public FCQueueNodeBackwardIterator(final FCQueue<E> queue, final FCQueueNode<E> head, final FCQueueNode<E> tail) {
		super(queue, tail, head);
	}


	@Override
	FCQueueNode<E> nextNode() {
		return current.getTowardHead();
	}
}

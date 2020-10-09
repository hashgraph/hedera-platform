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

import com.swirlds.common.FastCopyable;

/**
 * One node in the linked list that holds the contents of one or more FCQueue queues. It forms a doubly linked list,
 * without any sentinels at the ends, and with a reference count in each node that reflects how many head and tail
 * pointers exist that point to it.
 *
 * The list at all times maintains the invariant that the head and tail have nonzero refcounts.  If either refcount
 * ever becomes zero, then it automatically removes enough of them to regain the invariant.
 */
class FCQueueNode<E extends FastCopyable<E>> {

	/** the element in the list */
	E element;

	/** number of queues in this queue group that have a head or tail pointing to this node */
	private long refCount;

	/** the next node in the direction toward the head, or null if none */
	FCQueueNode<E> towardHead;

	/** the next node in the direction toward the tail, or null if none */
	FCQueueNode<E> towardTail;

	/**
	 * instantiate a single-node list with a ref count of 2 (because head and tail will point to it)
	 *
	 * @param element
	 * 		the FastCopyable element in the list held by this node
	 */
	FCQueueNode(final E element) {
		refCount = 2;
		towardTail = null;
		towardHead = null;
		this.element = element;
	}

	/**
	 * create a new node with a refCount of 1, and insert it next to this one on the tail side. This one must not
	 * already
	 * have one there
	 *
	 * @param element
	 * 		the FastCopyable element to insert at the tail
	 * @return the new node
	 * @throws IllegalArgumentException
	 * 		this insertion was somewhere other than the tail
	 */
	FCQueueNode<E> insertAtTail(final E element) throws IllegalArgumentException {
		if (this.towardTail != null) {
			throw new IllegalArgumentException("FCQueue tried to insert somewhere other than the tail");
		}

		FCQueueNode<E> node = new FCQueueNode<>(element);
		node.refCount = 1; //this will be the new tail, with only the original pointing to it to start with
		node.towardHead = this;
		node.towardTail = null;
		towardTail = node;

		return node;
	}

	/**
	 * increment the refCount for this node, and return the new refCount
	 *
	 * @return the new refCount
	 */

	long incRefCount() {
		refCount++;
		return refCount;
	}

	/**
	 * decrement the refCount for this node, and return the new refCount. Also remove all consecutive nodes at either
	 * end with zero refcount
	 *
	 * @return the new refCount
	 */
	long decRefCount() {
		refCount--;

		//if this reaches zero and is at the head, then walk toward the tail, deleting all the consecutive zeros
		for (FCQueueNode<E> q = this; q != null && q.refCount == 0 && q.towardHead == null; q = q.towardTail) {
			if (q.towardTail != null) { //if there's another node pointing to q
				q.towardTail.towardHead = null; //then remove q from the list
			}
		}

		//if this reaches zero and is at the tail, then walk toward the head, deleting all the consecutive zeros
		for (FCQueueNode<E> q = this; q != null && q.refCount == 0 && q.towardTail == null; q = q.towardHead) {
			if (q.towardHead != null) { //if there's another node pointing to q
				q.towardHead.towardTail = null; //remove q from the list
			}
		}

		return refCount;
	}
}

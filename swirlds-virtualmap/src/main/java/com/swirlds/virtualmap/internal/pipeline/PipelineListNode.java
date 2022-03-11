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

package com.swirlds.virtualmap.internal.pipeline;

/**
 * A single link in a {@link PipelineList}.
 *
 * @param <T>
 * 		the type of the data contained
 */
class PipelineListNode<T> {

	private final T value;

	private PipelineListNode<T> previous;
	private PipelineListNode<T> next;

	PipelineListNode(final T value) {
		this.value = value;
	}

	/**
	 * Get the value contained by this node.
	 *
	 * @return the value
	 */
	T getValue() {
		return value;
	}

	/**
	 * Get the previous node, or null if there is no previous node.
	 *
	 * @return the previous node
	 */
	synchronized PipelineListNode<T> getPrevious() {
		return previous;
	}

	/**
	 * Get the next node, or null if there is no next node.
	 *
	 * @return the next node
	 */
	synchronized PipelineListNode<T> getNext() {
		return next;
	}

	/**
	 * Remove a node from the list. Intentionally package private.
	 */
	synchronized void remove() {
		if (previous != null) {
			previous.next = next;
		}
		if (next != null) {
			next.previous = previous;
		}
	}

	/**
	 * Add the next node in the linked list. Intentionally package private.
	 *
	 * @param nextNode
	 * 		the node to add
	 */
	synchronized void addNext(final PipelineListNode<T> nextNode) {
		if (next != null) {
			throw new IllegalStateException("this list does not currently support insertions in the middle");
		}
		if (nextNode.previous != null) {
			throw new IllegalStateException("element to be inserted already has a previous value");
		}
		next = nextNode;
		nextNode.previous = this;
	}

	@Override
	public String toString() {
		return "(" + value + ")";
	}
}

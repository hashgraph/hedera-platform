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

package com.swirlds.common.merkle.synchronization.views;

/**
 * Describes methods that implement a queue of nodes to be handled by the teacher during reconnect. Nodes are placed
 * into this queue when they are sent in a query, and handled when it is time to send the lesson for the node.
 *
 * @param <T>
 * 		a type that represents a merkle node in the view
 */
public interface TeacherHandleQueue<T> {

	/**
	 * <p>
	 * Add the node to a queue so that it can be later handled. Nodes added to this queue should be
	 * returned by {@link #getNextNodeToHandle()}. Queue does not need to be thread safe.
	 * </p>
	 *
	 * <p>
	 * May not be called on all nodes. For the nodes it is called on, the order will be consistent with
	 * a breadth first ordering.
	 * </p>
	 *
	 * @param node
	 * 		the node to add to the queue
	 */
	void addToHandleQueue(T node);

	/**
	 * Remove and return the next node from the queue built by {@link #addToHandleQueue(Object)}.
	 *
	 * @return the next node to handle
	 */
	T getNextNodeToHandle();

	/**
	 * Check if there is anything in the queue built by {@link #addToHandleQueue(Object)}.
	 *
	 * @return true if there are nodes in the queue
	 */
	boolean areThereNodesToHandle();

}

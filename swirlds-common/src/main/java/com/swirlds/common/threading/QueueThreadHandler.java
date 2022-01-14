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

package com.swirlds.common.threading;

/**
 * A method that an {@link QueueThread} can use to handle elements in the queue.
 */
@FunctionalInterface
public interface QueueThreadHandler<T> {

	/**
	 * Handle an item from the queue.
	 *
	 * @param item
	 * 		an item from the queue. Will never be null.
	 * @throws InterruptedException
	 * 		if the thread is interrupted while work is being done
	 */
	void handle(T item) throws InterruptedException;

}

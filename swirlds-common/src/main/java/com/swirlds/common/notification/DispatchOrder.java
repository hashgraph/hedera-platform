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

package com.swirlds.common.notification;

/**
 * Defines how the dispatcher handles the delivery of {@link Notification} to each registered {@link Listener}
 * implementation.
 */
public enum DispatchOrder {
	/**
	 * Provides no guarantees in terms of ordering when the dispatcher is called from multiple threads for the same
	 * {@link Listener} class.
	 *
	 * If used with {@link DispatchMode#SYNC}, then all {@link Notification} dispatched from a single thread will be in
	 * order.
	 */
	UNORDERED,

	/**
	 * Provides a best effort ordering guarantee that {@link Listener} implementations will be notified in the original
	 * order the {@link Notification} were dispatched.
	 */
	ORDERED
}

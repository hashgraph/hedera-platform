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
 * Defines how the dispatcher for a given {@link Listener} operates with respect to the caller.
 */
public enum DispatchMode {
	/**
	 * Blocking mode which guarantees that the {@link Notification} will have been successfully dispatched to all
	 * registered {@link Listener} implementations before returning.
	 *
	 * The only guarantees provided are that the caller will be blocked until all registered listeners have been
	 * notified and that any exceptions thrown by a listener implementation will be propagated to the caller.
	 */
	SYNC,

	/**
	 * Queues the notification for delivery and returns control to the caller as quickly as possible.
	 * Any exceptions thrown will be available via the {@link NotificationResult#getExceptions()} method.
	 */
	ASYNC
}

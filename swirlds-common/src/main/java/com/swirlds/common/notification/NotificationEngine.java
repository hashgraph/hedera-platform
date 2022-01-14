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

package com.swirlds.common.notification;

import java.util.concurrent.Future;

/**
 * Provides a lightweight and extensible (event) notification engine supporting both synchronous and asynchronous event
 * models along with unordered or ordered delivery of notifications.
 */
public interface NotificationEngine {

	/**
	 * Prepares the engine for use and acquires any necessary threads or external resources.
	 */
	void initialize();

	/**
	 * Shuts down all underlying resources acquired during initialization and operation. This includes any threads or
	 * other external resources that must be explicitly released.
	 */
	void shutdown();

	/**
	 * Dispatches a {@link Notification} instance to the listeners of the specified type.
	 *
	 * If the listener class uses {@link DispatchMode#SYNC} then this method will block until all registered listeners
	 * have been notified and the {@link Future} returned by this method will already be complete.
	 *
	 * However, If the listener class uses {@link DispatchMode#ASYNC} then this method will return immediately after the
	 * dispatch request has been given to the dispatcher. Only when the returned {@link Future} is resolved will all the
	 * registered listeners have been notified. Any exceptions thrown during listener notification will be provided by
	 * the {@link NotificationResult#getExceptions()} method.
	 *
	 * @param listenerClass
	 * 		the type of listener to which the given notification should be sent
	 * @param notification
	 * 		the notification to be sent to all registered listeners
	 * @param <L>
	 * 		the type of the {@link Listener} class
	 * @param <N>
	 * 		the type of the {@link Notification} class
	 * @return a {@link Future} that upon resolution provides the status of the dispatch request as {@link
	 *        NotificationResult} instance
	 */
	<L extends Listener<N>, N extends Notification> Future<NotificationResult<N>> dispatch(final Class<L> listenerClass,
			final N notification);

	/**
	 * Registers a concrete {@link Listener} implementation with the underlying dispatcher.
	 *
	 * @param listenerClass
	 * 		the type of listener for which the concrete implementation should be registered
	 * @param callback
	 * 		the concrete listener implementation to be registered
	 * @param <L>
	 * 		the type of the {@link Listener} class
	 * @return true if the listener was successfully registered with the dispatcher; otherwise false
	 */
	<L extends Listener> boolean register(final Class<L> listenerClass, final L callback);

	/**
	 * Removes a concrete {@link Listener} implementation from the underlying dispatcher.
	 *
	 * @param listenerClass
	 * 		the type of listener for which the concrete implementation should be unregistered
	 * @param callback
	 * 		the concrete listener implementation to be unregistered
	 * @param <L>
	 * 		the type of the {@link Listener} class
	 * @return true if the listener was successfully unregistered from the dispatcher; otherwise false
	 */
	<L extends Listener> boolean unregister(final Class<L> listenerClass, final L callback);

}

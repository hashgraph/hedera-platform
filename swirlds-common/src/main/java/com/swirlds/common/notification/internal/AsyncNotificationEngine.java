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

package com.swirlds.common.notification.internal;

import com.swirlds.common.futures.WaitingFuture;
import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.NoListenersAvailableException;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * {@inheritDoc}
 */
public class AsyncNotificationEngine extends AbstractNotificationEngine {

	/**
	 * the internal listener registry that associates a given type of {@link Listener} with a {@link Dispatcher}
	 */
	private Map<Class<? extends Listener>, Dispatcher<? extends Listener>> listenerRegistry;


	/**
	 * Default Constructor.
	 */
	public AsyncNotificationEngine() {
		this.listenerRegistry = new ConcurrentHashMap<>();
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void initialize() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() {
		for (Dispatcher<?> dispatcher : listenerRegistry.values()) {
			if (dispatcher.isRunning()) {
				dispatcher.stop();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <L extends Listener<N>, N extends Notification> Future<NotificationResult<N>> dispatch(
			final Class<L> listenerClass, final N notification) {

		checkArguments(listenerClass, notification);

		final DispatchOrder dispatchOrder = dispatchOrder(listenerClass);
		final DispatchMode dispatchMode = dispatchMode(listenerClass);

		final WaitingFuture<NotificationResult<N>> future = new WaitingFuture<>();

		try {
			invokeWithDispatcher(dispatchOrder, listenerClass, (dispatcher) -> {
				assignSequence(notification);

				if (dispatchMode == DispatchMode.ASYNC) {
					dispatcher.notifyAsync(notification, future::done);
				} else {
					dispatcher.notifySync(notification, future::done);
				}
			});
		} catch (NoListenersAvailableException ex) {
			future.done(new NotificationResult<>(notification, 0));
		}

		return future;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <L extends Listener> boolean register(final Class<L> listenerClass, final L callback) {

		checkArguments(listenerClass, callback);

		final Dispatcher<L> dispatcher = ensureDispatcherExists(listenerClass);

		return dispatcher.addListener(callback);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <L extends Listener> boolean unregister(final Class<L> listenerClass, final L callback) {

		checkArguments(listenerClass, callback);

		final Dispatcher<L> dispatcher = ensureDispatcherExists(listenerClass);

		return dispatcher.removeListener(callback);
	}

	private <L extends Listener<N>, N extends Notification> void checkArguments(final Class<L> listenerClass,
			final N notification) {
		if (listenerClass == null) {
			throw new IllegalArgumentException("listenerClass");
		}

		if (notification == null) {
			throw new IllegalArgumentException("notification");
		}
	}

	private <L extends Listener> void checkArguments(final Class<L> listenerClass, final L callback) {
		if (listenerClass == null) {
			throw new IllegalArgumentException("listenerClass");
		}

		if (callback == null) {
			throw new IllegalArgumentException("callback");
		}
	}

	private <L extends Listener> Dispatcher<L> ensureDispatcherExists(final Class<L> listenerClass) {
		@SuppressWarnings("unchecked") Dispatcher<L> dispatcher = (Dispatcher<L>) listenerRegistry.putIfAbsent(
				listenerClass,
				new Dispatcher<>(listenerClass));

		if (dispatcher == null) {
			dispatcher = new Dispatcher<>(listenerClass);
			listenerRegistry.put(listenerClass, dispatcher);
		}

		return dispatcher;
	}

	private <L extends Listener<N>, N extends Notification> void invokeWithDispatcher(final DispatchOrder order,
			final Class<L> listenerClass, final Consumer<Dispatcher<L>> method) throws NoListenersAvailableException {
		@SuppressWarnings("unchecked") final Dispatcher<L> dispatcher = (Dispatcher<L>) listenerRegistry.get(
				listenerClass);

		if (dispatcher == null) {
			throw new NoListenersAvailableException();
		}

		if (order == DispatchOrder.ORDERED) {
			synchronized (dispatcher.getMutex()) {
				method.accept(dispatcher);
			}
		} else {
			method.accept(dispatcher);
		}
	}


}

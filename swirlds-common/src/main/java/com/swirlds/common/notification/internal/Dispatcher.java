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

import com.swirlds.common.notification.DispatchException;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.threading.StandardThreadFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;

public class Dispatcher<L extends Listener> {

	private static final int THREAD_STOP_WAIT_MS = 5000;
	private static final int THREAD_PRIORITY = 5;

	private final PriorityBlockingQueue<DispatchTask<?, ?>> asyncDispatchQueue;

	private final String listenerClassName;

	private final Object mutex;

	private final List<L> listeners;

	private volatile Thread dispatchThread;

	private volatile boolean running;

	public Dispatcher(final Class<L> listenerClass) {
		this.mutex = new Object();
		this.listeners = new CopyOnWriteArrayList<>();
		this.asyncDispatchQueue = new PriorityBlockingQueue<>();
		this.listenerClassName = listenerClass.getSimpleName();
	}

	public Object getMutex() {
		return mutex;
	}

	public synchronized boolean isRunning() {
		return running && dispatchThread != null && dispatchThread.isAlive();
	}

	public synchronized void start() {
		if (dispatchThread != null && dispatchThread.isAlive()) {
			stop();
		}

		dispatchThread = StandardThreadFactory.newThread(
				String.format("< notify dispatch: %s >", listenerClassName),
				this::worker,
				true,
				THREAD_PRIORITY
		);

		running = true;
		dispatchThread.start();
	}

	public synchronized void stop() {
		running = false;

		if (asyncDispatchQueue.size() == 0) {
			dispatchThread.interrupt();
		}

		try {
			dispatchThread.join(THREAD_STOP_WAIT_MS);

			if (dispatchThread.isAlive() && !dispatchThread.isInterrupted()) {
				dispatchThread.interrupt();
			}

			dispatchThread = null;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public <N extends Notification> void notifySync(final N notification,
			final Consumer<NotificationResult<N>> callback) {
		handleDispatch(notification, true, callback);
	}

	public <N extends Notification> void notifyAsync(final N notification,
			final Consumer<NotificationResult<N>> callback) {
		if (!isRunning()) {
			start();
		}

		asyncDispatchQueue.put(new DispatchTask<>(notification, callback));
	}

	public synchronized boolean addListener(final L listener) {
		return listeners.add(listener);
	}

	public synchronized boolean removeListener(final L listener) {
		return listeners.remove(listener);
	}

	private <N extends Notification> void handleDispatch(final N notification, final boolean throwOnError,
			final Consumer<NotificationResult<N>> callback) {

		final NotificationResult<N> result = new NotificationResult<>(notification, listeners.size());

		for (final L l : listeners) {
			try {
				@SuppressWarnings("unchecked") final Listener<N> listener = (Listener<N>) l;
				listener.notify(notification);
			} catch (Exception ex) {
				if (throwOnError) {
					throw new DispatchException(ex);
				}

				result.addException(ex);
			}
		}

		if (callback != null) {
			callback.accept(result);
		}
	}

	private void worker() {
		try {
			while (running || asyncDispatchQueue.size() > 0) {
				@SuppressWarnings("unchecked") final DispatchTask<Listener<Notification>, Notification> task =
						(DispatchTask<Listener<Notification>, Notification>) asyncDispatchQueue.take();

				handleDispatch(task.getNotification(), false, task.getCallback());
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}

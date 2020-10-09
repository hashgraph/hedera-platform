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

import java.util.ArrayList;
import java.util.List;

/**
 * Provides information and errors as the result of a given {@link NotificationEngine#dispatch(Class, Notification)}
 * call.
 *
 * @param <N>
 * 		the type of the {@link Notification} class
 */
public class NotificationResult<N extends Notification> {

	/**
	 * the original notification that was sent to each listener
	 */
	private N notification;

	/**
	 * the total number of registered and available listeners at the time of the dispatch
	 */
	private int totalListeners;

	/**
	 * the list of exceptions, if any, that were thrown by the listeners
	 */
	private List<Exception> exceptions;

	/**
	 * Creates a new instance with no exceptions and the given number of registered listeners.
	 *
	 * @param notification
	 * 		the original notification that was sent to each listener
	 * @param totalListeners
	 * 		the total number of registered listeners
	 */
	public NotificationResult(final N notification, final int totalListeners) {
		this(notification, totalListeners, null);
	}

	/**
	 * Creates a new instance with the provided list of exceptions and the given number of registered listeners.
	 *
	 * @param notification
	 * 		the original notification that was sent to each listener
	 * @param totalListeners
	 * 		the total number of registered listeners
	 * @param exceptions
	 * 		the list of exceptions that occurred during listener invocation
	 */
	public NotificationResult(final N notification, final int totalListeners, final List<Exception> exceptions) {
		if (notification == null) {
			throw new IllegalArgumentException("notification");
		}

		this.notification = notification;
		this.totalListeners = totalListeners;
		this.exceptions = (exceptions != null) ? exceptions : new ArrayList<>();
	}

	/**
	 * Getter that returns the original notification that was sent to each listener.
	 *
	 * @return the original notification that was sent to each listener
	 */
	public N getNotification() {
		return notification;
	}

	/**
	 * Getter that returns the total number of registered listeners at the time of dispatch.
	 *
	 * @return the total number of registered listeners
	 */
	public int getTotalListeners() {
		return totalListeners;
	}

	/**
	 * Getter that returns a list of {@link Exception} instances that were thrown during listener invocation.
	 *
	 * @return the list of exceptions, if any, thrown during listener invocation
	 */
	public List<Exception> getExceptions() {
		return exceptions;
	}

	/**
	 * Getter that returns the total number of failed listener invocations.
	 *
	 * @return the total number of failed listener invocations
	 */
	public int getFailureCount() {
		return exceptions.size();
	}

	/**
	 * Adds an {@link Exception} to the internal {@link List} of exceptions.
	 *
	 * @param ex
	 * 		the exception to be added
	 */
	public void addException(final Exception ex) {
		exceptions.add(ex);
	}
}

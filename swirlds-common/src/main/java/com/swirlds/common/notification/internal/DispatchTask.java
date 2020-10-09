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

import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationResult;

import java.util.function.Consumer;

public class DispatchTask<L extends Listener<N>, N extends Notification> implements Comparable<DispatchTask<L, N>> {

	private N notification;

	private Consumer<NotificationResult<N>> callback;

	public DispatchTask(final N notification, final Consumer<NotificationResult<N>> callback) {
		if (notification == null) {
			throw new IllegalArgumentException("notification");
		}

		this.notification = notification;
		this.callback = callback;
	}

	public N getNotification() {
		return notification;
	}

	public Consumer<NotificationResult<N>> getCallback() {
		return callback;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(final DispatchTask<L, N> that) {
		final int EQUAL = 0;
		final int GREATER_THAN = 1;

		if (this == that) {
			return EQUAL;
		}

		if (that == null) {
			return GREATER_THAN;
		}

		return Long.compare(this.notification.getSequence(), that.notification.getSequence());
	}
}

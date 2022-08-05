/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

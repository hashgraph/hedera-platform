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

package com.swirlds.common.notification.internal;

import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchModel;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractNotificationEngine implements NotificationEngine {

	private Map<Class<? extends Listener>, DispatchMode> listenerModeCache;
	private Map<Class<? extends Listener>, DispatchOrder> listenerOrderCache;
	private AtomicLong sequence;

	public AbstractNotificationEngine() {
		this.listenerModeCache = new HashMap<>();
		this.listenerOrderCache = new HashMap<>();
		this.sequence = new AtomicLong(0);
	}

	protected synchronized <L extends Listener> DispatchMode dispatchMode(final Class<L> listenerClass) {
		if (listenerModeCache.containsKey(listenerClass)) {
			return listenerModeCache.get(listenerClass);
		}

		final DispatchModel model = listenerClass.getAnnotation(DispatchModel.class);
		DispatchMode mode = DispatchMode.SYNC;

		if (model != null) {
			mode = model.mode();
		}

		listenerModeCache.putIfAbsent(listenerClass, mode);
		return mode;
	}

	protected synchronized <L extends Listener> DispatchOrder dispatchOrder(final Class<L> listenerClass) {
		if (listenerOrderCache.containsKey(listenerClass)) {
			return listenerOrderCache.get(listenerClass);
		}

		final DispatchModel model = listenerClass.getAnnotation(DispatchModel.class);
		DispatchOrder order = DispatchOrder.UNORDERED;

		if (model != null) {
			order = model.order();
		}

		listenerOrderCache.putIfAbsent(listenerClass, order);
		return order;
	}

	protected <N extends Notification> void assignSequence(final N notification) {
		if (notification == null) {
			throw new IllegalArgumentException("notification");
		}

		if (notification.getSequence() != 0) {
			return;
		}

		notification.setSequence(sequence.incrementAndGet());
	}
}

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

import com.swirlds.common.notification.internal.AsyncNotificationEngine;

/**
 * Factory that provides access to the default implementation of the {@link NotificationEngine} interface using a
 * static singleton pattern.
 */
public final class NotificationFactory {

	/**
	 * the internal singleton instance
	 */
	private static final NotificationEngine instance = new AsyncNotificationEngine();

	static {
		instance.initialize();
	}

	/**
	 * Private constructor to prevent class instantiation.
	 */
	private NotificationFactory() {

	}

	/**
	 * Getter that provides access to a singleton instance of the {@link NotificationEngine} interface.
	 *
	 * @return a singleton instance of the {@link NotificationEngine}
	 */
	public static NotificationEngine getEngine() {
		return instance;
	}
}

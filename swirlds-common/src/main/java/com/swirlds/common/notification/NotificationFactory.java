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

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

package com.swirlds.platform.system;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchModel;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.NotificationFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.system.SystemUtils.exitSystem;

/**
 * Use the methods in this class to tear down the system. To be used in situations
 * where things have gone off the rails and automated recovery is not possible.
 */
public final class Fatal {

	private static final Logger LOG = LogManager.getLogger(Fatal.class);

	/**
	 * The notification type for a fatal event.
	 */
	public static class FatalNotification extends AbstractNotification {

	}

	/**
	 * Describes an object that listens for fatal events.
	 */
	@DispatchModel(mode = DispatchMode.SYNC, order = DispatchOrder.ORDERED)
	public interface FatalListener extends Listener<FatalNotification> {

	}

	private Fatal() {

	}

	/**
	 * Declare a fatal error. Causes the system to tear itself down.
	 *
	 * @param message
	 * 		a description of the error
	 */
	public static void fatalError(final String message) {
		fatalError(message, null, SystemExitReason.FATAL_ERROR);
	}

	/**
	 * Declare a fatal error. Causes the system to tear itself down.
	 *
	 * @param message
	 * 		a description of the error
	 * @param code
	 * 		the exception code that will be returned on exit
	 */
	public static void fatalError(final String message, final SystemExitReason code) {
		fatalError(message, null, code);
	}

	/**
	 * Declare a fatal error. Causes the system to tear itself down.
	 *
	 * @param message
	 * 		a description of the error
	 * @param exception
	 * 		the exception that led to the error
	 */
	public static void fatalError(final String message, final Throwable exception) {
		fatalError(message, exception, SystemExitReason.FATAL_ERROR);
	}

	/**
	 * Declare a fatal error. Causes the system to tear itself down.
	 *
	 * @param message
	 * 		a description of the error
	 * @param exception
	 * 		the exception that led to the error
	 * @param code
	 * 		the exception code that will be returned on exit
	 */
	public static void fatalError(final String message, final Throwable exception, final SystemExitReason code) {

		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

		final StringBuilder sb = new StringBuilder();
		sb.append("Fatal error, node will shut down. Reason: ").append(message).append("\n");

		for (final StackTraceElement element : stackTrace) {
			sb.append("   ").append(element).append("\n");
		}

		if (exception == null) {
			LOG.fatal(EXCEPTION.getMarker(), sb);
		} else {
			LOG.fatal(EXCEPTION.getMarker(), sb, exception);
		}

		try {
			NotificationFactory.getEngine().dispatch(FatalListener.class, new FatalNotification());
		} catch (final Exception ex) {
			LOG.fatal(EXCEPTION.getMarker(), "Exception in fatal callback", ex);
		}

		exitSystem(code);
	}
}

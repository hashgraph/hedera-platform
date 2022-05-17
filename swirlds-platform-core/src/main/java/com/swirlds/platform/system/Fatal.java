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

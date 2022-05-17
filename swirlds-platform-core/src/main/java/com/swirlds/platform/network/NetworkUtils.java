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

package com.swirlds.platform.network;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.sync.SyncTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.io.Closeable;
import java.io.IOException;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;

public final class NetworkUtils {
	private static final Logger LOG = LogManager.getLogger();

	private NetworkUtils() {
	}

	/**
	 * Close all the {@link Closeable} instances supplied, ignoring any exceptions
	 *
	 * @param closeables
	 * 		the instances to close
	 */
	public static void close(final Closeable... closeables) {
		for (final Closeable closeable : closeables) {
			try {
				if (closeable != null) {
					closeable.close();
				}
			} catch (final IOException | RuntimeException ignored) {
				// we try to close, but ignore any issues if we fail
			}
		}
	}

	/**
	 * Called when an exception happens while executing something that uses a connection. This method will close the
	 * connection supplied and log the exception with an appropriate marker.
	 *
	 * @param e
	 * 		the exception that was thrown
	 * @param connection
	 * 		the connection used when the exception was thrown
	 * @throws InterruptedException
	 * 		if the provided exception is an {@link InterruptedException}, it will be rethrown once the connection is
	 * 		closed
	 */
	public static void handleNetworkException(final Exception e, final SyncConnection connection)
			throws InterruptedException {
		final String description;
		// always disconnect when an exception gets thrown
		if (connection != null) {
			connection.disconnect();
			description = connection.getDescription();
		} else {
			description = null;
		}
		if (e instanceof InterruptedException ie) {
			// we must make sure that the network thread can be interrupted
			throw ie;
		}
		// we use a different marker depending on what the root cause is
		LOG.error(determineExceptionMarker(e),
				"Connection broken: {}", description, e);
	}

	/**
	 * Determines the log marker to use for a connection exception based on the nested exception types
	 *
	 * @param e
	 * 		the exception thrown during a network operations
	 * @return the marker to use for logging
	 */
	public static Marker determineExceptionMarker(final Exception e) {
		return Utilities.isCausedByIOException(e) ||
				Utilities.isRootCauseSuppliedType(e, SyncTimeoutException.class)
				? SOCKET_EXCEPTIONS.getMarker()
				: EXCEPTION.getMarker();
	}
}

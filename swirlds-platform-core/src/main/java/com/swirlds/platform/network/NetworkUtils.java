/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.platform.network;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.sync.SyncTimeoutException;
import java.io.Closeable;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

public final class NetworkUtils {
    private static final Logger LOG = LogManager.getLogger();

    private NetworkUtils() {}

    /**
     * Close all the {@link Closeable} instances supplied, ignoring any exceptions
     *
     * @param closeables the instances to close
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
     * Called when an exception happens while executing something that uses a connection. This
     * method will close the connection supplied and log the exception with an appropriate marker.
     *
     * @param e the exception that was thrown
     * @param connection the connection used when the exception was thrown
     * @throws InterruptedException if the provided exception is an {@link InterruptedException}, it
     *     will be rethrown once the connection is closed
     */
    public static void handleNetworkException(final Exception e, final Connection connection)
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
        LOG.error(determineExceptionMarker(e), "Connection broken: {}", description, e);
    }

    /**
     * Determines the log marker to use for a connection exception based on the nested exception
     * types
     *
     * @param e the exception thrown during a network operations
     * @return the marker to use for logging
     */
    public static Marker determineExceptionMarker(final Exception e) {
        return Utilities.isCausedByIOException(e)
                        || Utilities.isRootCauseSuppliedType(e, SyncTimeoutException.class)
                ? SOCKET_EXCEPTIONS.getMarker()
                : EXCEPTION.getMarker();
    }
}

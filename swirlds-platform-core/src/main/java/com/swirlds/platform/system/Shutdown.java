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
package com.swirlds.platform.system;

import static com.swirlds.logging.LogMarker.STARTUP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A utility for shutting down the JVM. */
public final class Shutdown {

    private static final Logger LOG = LogManager.getLogger(Shutdown.class);

    private Shutdown() {}

    /**
     * Shut down the JVM.
     *
     * @param reason the reason the JVM is being shut down
     * @param exitCode the exit code to return when the JVM has been shut down
     */
    public static void shutdown(final String reason, final Integer exitCode) {
        LOG.info(STARTUP.getMarker(), "Node shutting down. Reason: {}", reason);
        System.exit(exitCode);
    }
}

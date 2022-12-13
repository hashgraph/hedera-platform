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
package com.swirlds.platform;

import static com.swirlds.common.utility.Units.BYTES_TO_BITS;
import static com.swirlds.common.utility.Units.MILLISECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransactionBitsPerSecond;
import com.swirlds.common.system.transaction.internal.SystemTransactionPing;
import com.swirlds.platform.network.NetworkMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This class is responsible for creating system transactions containing network statistics. */
public final class NetworkStatsTransmitter {

    private static final Logger LOG = LogManager.getLogger(NetworkStatsTransmitter.class);

    private NetworkStatsTransmitter() {}

    /** Create system transactions that transmit stats information. */
    public static void transmitStats(
            final SwirldsPlatform platform, final NetworkMetrics networkMetrics) {
        // send a transaction giving the average throughput sent from self to all others (in bits
        // per second)
        if (SettingsCommon.enableBpsTrans) {
            final int n = networkMetrics.getAvgBytePerSecSent().size();
            final long[] avgBitsPerSecSent = new long[n];
            for (int i = 0; i < n; i++) {
                avgBitsPerSecSent[i] =
                        (long) networkMetrics.getAvgPingMilliseconds().get(i).get() * BYTES_TO_BITS;
            }
            final SystemTransaction systemTransaction =
                    new SystemTransactionBitsPerSecond(avgBitsPerSecSent);
            final boolean good = platform.createSystemTransaction(systemTransaction);
            if (!good) {
                LOG.error(
                        EXCEPTION.getMarker(),
                        "failed to create bits-per-second system transaction)");
            }
        }

        // send a transaction giving the average ping time from self to all others (in microseconds)
        if (SettingsCommon.enablePingTrans) {
            final int n = networkMetrics.getAvgPingMilliseconds().size();
            final int[] avgPingMilliseconds = new int[n];
            for (int i = 0; i < n; i++) {
                avgPingMilliseconds[i] =
                        (int)
                                (networkMetrics.getAvgPingMilliseconds().get(i).get()
                                        * MILLISECONDS_TO_SECONDS);
            }
            final SystemTransaction systemTransaction =
                    new SystemTransactionPing(avgPingMilliseconds);
            final boolean good = platform.createSystemTransaction(systemTransaction);
            if (!good) {
                LOG.error(EXCEPTION.getMarker(), "failed to create ping time system transaction)");
            }
        }
    }
}

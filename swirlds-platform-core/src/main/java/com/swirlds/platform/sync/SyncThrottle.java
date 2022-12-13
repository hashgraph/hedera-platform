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
package com.swirlds.platform.sync;

import com.swirlds.platform.Connection;
import com.swirlds.platform.SettingsProvider;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A type to determine whether to slow down two nodes immediately after a sync, and if so, by how
 * much. The purpose is to slow down two nodes that are fast to allow nodes that are not as fast to
 * catch up. The current implementation determines a number of extra bytes to write into a {@link
 * SyncOutputStream} based upon a threshold {@link SettingsProvider#getThrottle7MaxBytes()}
 */
public final class SyncThrottle {

    /** The minimum number of received events to prevent writing extra bytes */
    private final long fallingBehindThreshold;

    /** Random generator used to write random bytes */
    private final Random random;

    /** provides system settings */
    private final SettingsProvider settings;

    /**
     * Constructor
     *
     * @param numberOfNodes the number of nodes in the network
     */
    public SyncThrottle(final long numberOfNodes, final SettingsProvider settings) {
        this.settings = settings;
        this.fallingBehindThreshold = (long) (numberOfNodes * settings.getThrottle7Threshold());
        this.random = ThreadLocalRandom.current();
    }

    /**
     * Determines if throttle7 should be applied. If both nodes are below the threshold (i.e. they
     * are both fast), then return {@code true} so that both nodes send extra bytes to slow
     * themselves down to allow slower nodes to catch up. Otherwise, return {@code false}.
     *
     * @param nEventsReceived the number of events received
     * @param nEventsSent the number of events sent
     * @return if throttle should be applied
     */
    public boolean shouldThrottle(final int nEventsReceived, final int nEventsSent) {
        return nEventsReceived < fallingBehindThreshold && nEventsSent < fallingBehindThreshold;
    }

    /**
     * Compute and write a number of bytes into a {@link SyncOutputStream} referenced by a {@link
     * Connection}.
     *
     * @param conn the {@link Connection} object to which this throttle will be applied
     * @return a callable that will return the number of bytes written
     */
    public Callable<Integer> sendThrottleBytes(final Connection conn) {
        return () -> {
            final long bytesSent = conn.getDos().getSyncByteCounter().getCount();

            final int nThrottleBytes = getNumThrottleBytes((int) bytesSent);

            writeRandomInts(conn, nThrottleBytes / Integer.BYTES);
            conn.getDos().flush();

            return nThrottleBytes;
        };
    }

    /**
     * Receives the maximum allowed throttle7 bytes and discards them.
     *
     * @param conn the connection to receive throttle7 bytes from
     * @return a callable that returns void
     */
    public Callable<Void> receiveThrottleBytes(final Connection conn) {
        return () -> {
            int maxToRead = settings.getThrottle7MaxBytes() / Integer.BYTES;

            int len = conn.getDis().readInt();

            if (len > maxToRead) {
                throw new IOException(
                        String.format(
                                "The input stream provided a length of %d throttle7 ints which"
                                        + " exceeds the max of %d",
                                len, maxToRead));
            }

            // read and discard any ints for slowing the sync
            for (int i = 0; i < len; i++) {
                conn.getDis().readInt();
            }

            // (ignored)
            return null;
        };
    }

    /**
     * Compute the number of bytes to write. This function is the entire dependency of this type on
     * settings values in {@link SettingsProvider}
     *
     * @param bytesSent the number of bytes sent before extra bytes are to be written
     * @return the number of extra bytes to write
     */
    private int getNumThrottleBytes(final int bytesSent) {
        int nThrottleBytes = (int) (1 + bytesSent * settings.getThrottle7Extra());

        if (nThrottleBytes > settings.getThrottle7MaxBytes()) {
            nThrottleBytes = settings.getThrottle7MaxBytes();
        }

        return nThrottleBytes;
    }

    /**
     * Unconditionally write a number of integers based, each with a pseudo -random value, into a
     * {@link SyncOutputStream} referenced by a {@link Connection}.
     *
     * @param conn the {@link Connection} object to which this throttle will be applied
     * @param throttleInts the number of ints to write
     * @throws IOException iff the {@link SyncOutputStream} referenced by {@code conn} throws
     */
    private void writeRandomInts(final Connection conn, final int throttleInts) throws IOException {
        conn.getDos().writeInt(throttleInts);
        for (int i = 0; i < throttleInts; i++) {
            conn.getDos().writeInt(random.nextInt());
        }
    }
}

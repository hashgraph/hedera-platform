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
package com.swirlds.platform.reconnect;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.unidirectional.NetworkProtocolResponder;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReconnectProtocolResponder implements NetworkProtocolResponder {
    private static final Logger LOG = LogManager.getLogger(ReconnectProtocolResponder.class);

    private final SignedStateManager signedStateManager;
    private final ReconnectSettings settings;
    /**
     * This object is responsible for limiting the frequency of reconnect attempts (in the role of
     * the sender)
     */
    private final ReconnectThrottle reconnectThrottle;

    private final ReconnectMetrics stats;
    private final ThreadManager threadManager;

    /**
     * @param threadManager responsible for managing thread lifecycles
     * @param signedStateManager responsible for managing signed state lifecycles
     * @param settings reconnect settings
     * @param reconnectThrottle limits when reconnect may start
     * @param stats reconnect metrics
     */
    public ReconnectProtocolResponder(
            final ThreadManager threadManager,
            final SignedStateManager signedStateManager,
            final ReconnectSettings settings,
            final ReconnectThrottle reconnectThrottle,
            final ReconnectMetrics stats) {
        this.threadManager = threadManager;
        this.signedStateManager = signedStateManager;
        this.settings = settings;
        this.reconnectThrottle = reconnectThrottle;
        this.stats = stats;
    }

    /** {@inheritDoc} */
    @Override
    public void protocolInitiated(final byte initialByte, final Connection connection)
            throws IOException, NetworkProtocolException {
        LOG.info(
                RECONNECT.getMarker(),
                "{} got COMM_STATE_REQUEST from {}",
                connection.getSelfId(),
                connection.getOtherId());

        if (!reconnectThrottle.initiateReconnect(connection.getOtherId().getId())) {
            ReconnectUtils.denyReconnect(connection);
            return;
        }

        try {
            ReconnectUtils.confirmReconnect(connection);

            // the SignedState is later manually released by the ReconnectTeacher
            final SignedState state = signedStateManager.getLatestSignedState(false).get();

            new ReconnectTeacher(
                            threadManager,
                            connection,
                            state,
                            settings.getAsyncStreamTimeoutMilliseconds(),
                            connection.getSelfId().getId(),
                            connection.getOtherId().getId(),
                            state.getRound(),
                            stats)
                    .execute();
        } finally {
            reconnectThrottle.markReconnectFinished();
        }
    }
}

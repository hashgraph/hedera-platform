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
package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Implements the emergency reconnect protocol over a bidirectional network */
public class EmergencyReconnectProtocol implements Protocol {
    private static final Logger LOG = LogManager.getLogger(EmergencyReconnectProtocol.class);
    private final NodeId peerId;
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ReconnectThrottle teacherThrottle;
    private final EmergencyStateFinder stateFinder;
    private final int reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private final Crypto crypto;
    private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;
    private final ThreadManager threadManager;
    private final NotificationEngine notificationEngine;

    /**
     * @param threadManager responsible for managing thread lifecycles
     * @param peerId the ID of the peer we are communicating with
     * @param emergencyRecoveryManager the state of emergency recovery, if any
     * @param teacherThrottle restricts reconnects as a teacher
     * @param stateFinder finds compatible states based on round number and hash
     * @param reconnectSocketTimeout the socket timeout to use when executing a reconnect
     * @param reconnectMetrics tracks reconnect metrics
     * @param reconnectController controls reconnecting as a learner
     * @param crypto the object that contains all key pairs and CSPRNG state for this member
     */
    public EmergencyReconnectProtocol(
            final ThreadManager threadManager,
            final NotificationEngine notificationEngine,
            final NodeId peerId,
            final EmergencyRecoveryManager emergencyRecoveryManager,
            final ReconnectThrottle teacherThrottle,
            final EmergencyStateFinder stateFinder,
            final int reconnectSocketTimeout,
            final ReconnectMetrics reconnectMetrics,
            final ReconnectController reconnectController,
            final Crypto crypto) {
        this.threadManager = threadManager;
        this.notificationEngine = notificationEngine;
        this.peerId = peerId;
        this.emergencyRecoveryManager = emergencyRecoveryManager;
        this.teacherThrottle = teacherThrottle;
        this.stateFinder = stateFinder;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.reconnectMetrics = reconnectMetrics;
        this.reconnectController = reconnectController;
        this.crypto = crypto;
    }

    @Override
    public boolean shouldInitiate() {
        final boolean initiateEmergencyReconnect =
                emergencyRecoveryManager.isEmergencyStateRequired();
        if (initiateEmergencyReconnect) {
            final boolean shouldInitiate = reconnectController.acquireLearnerPermit();
            if (shouldInitiate) {
                initiatedBy = InitiatedBy.SELF;
            }
            return shouldInitiate;
        }
        return false;
    }

    @Override
    public void initiateFailed() {
        reconnectController.cancelLearnerPermit();
        initiatedBy = InitiatedBy.NO_ONE;
    }

    @Override
    public boolean shouldAccept() {
        final boolean shouldAccept = teacherThrottle.initiateReconnect(peerId.getId());
        if (shouldAccept) {
            initiatedBy = InitiatedBy.PEER;
        }
        return shouldAccept;
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        // if both nodes are missing the emergency state, it makes no sense to reconnect with each
        // other
        // also, it would not be clear who the teacher and who the learner is
        return false;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            switch (initiatedBy) {
                case PEER -> new EmergencyReconnectTeacher(
                                threadManager,
                                stateFinder,
                                reconnectSocketTimeout,
                                reconnectMetrics)
                        .execute(connection);

                case SELF -> learner(connection);

                default -> throw new NetworkProtocolException(
                        String.format(
                                "runProtocol() called for emergency reconnect with peer %d but it"
                                        + " is unclear who the teacher and who the learner is",
                                peerId.getId()));
            }
        } finally {
            initiatedBy = InitiatedBy.NO_ONE;
        }
    }

    private void learner(final Connection connection) {
        registerReconnectCompleteListener();
        new EmergencyReconnectLearner(
                        emergencyRecoveryManager.getEmergencyRecoveryFile(),
                        reconnectController,
                        crypto)
                .execute(connection);
    }

    private void registerReconnectCompleteListener() {
        notificationEngine.register(
                ReconnectCompleteListener.class,
                notification -> {
                    if (emergencyRecoveryManager.isEmergencyStateRequired()) {
                        LOG.info(
                                RECONNECT.getMarker(),
                                "Emergency Reconnect Complete, round {} received from peer {}",
                                notification.getRoundNumber(),
                                peerId.getId());
                        emergencyRecoveryManager.emergencyStateLoaded();
                    }
                });
    }

    private enum InitiatedBy {
        NO_ONE,
        SELF,
        PEER
    }
}

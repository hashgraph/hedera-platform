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

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.common.utility.StringFormattingUtilities.formattedList;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignedState;
import java.io.IOException;
import java.net.SocketException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates reconnect logic for the up to date node which is helping an out of date
 * node obtain a recent state.
 */
public class ReconnectTeacher {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(ReconnectTeacher.class);

    private final Connection connection;
    private final SignedState signedState;
    private final int reconnectSocketTimeout;

    private final long selfId;
    private final long otherId;
    private final long lastRoundReceived;

    private final ReconnectMetrics statistics;

    /** After reconnect is finished, restore the socket timeout to the original value. */
    private int originalSocketTimeout;

    private boolean stateIsReleased;

    private final ThreadManager threadManager;

    /**
     * @param threadManager responsible for managing thread lifecycles
     * @param connection the connection to be used for the reconnect
     * @param signedState the signed state to send to the learner
     * @param reconnectSocketTimeout the socket timeout to use during the reconnect
     * @param selfId this node's ID
     * @param otherId the learner's ID
     * @param lastRoundReceived the round of the state
     * @param statistics reconnect metrics
     */
    public ReconnectTeacher(
            final ThreadManager threadManager,
            final Connection connection,
            final SignedState signedState,
            final int reconnectSocketTimeout,
            final long selfId,
            final long otherId,
            final long lastRoundReceived,
            final ReconnectMetrics statistics) {

        this.threadManager = threadManager;
        this.connection = connection;
        this.signedState = signedState;
        this.reconnectSocketTimeout = reconnectSocketTimeout;

        this.selfId = selfId;
        this.otherId = otherId;
        this.lastRoundReceived = lastRoundReceived;
        this.statistics = statistics;
    }

    /**
     * increase socketTimout before performing reconnect
     *
     * @throws ReconnectException thrown when there is an error in the underlying protocol
     */
    private void increaseSocketTimeout() throws ReconnectException {
        try {
            originalSocketTimeout = connection.getTimeout();
            connection.setTimeout(reconnectSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * Reset socketTimeout to original value
     *
     * @throws ReconnectException thrown when there is an error in the underlying protocol
     */
    private void resetSocketTimeout() throws ReconnectException {
        if (!connection.connected()) {
            logger.debug(
                    RECONNECT.getMarker(),
                    "{} connection to {} is no longer connected. Returning.",
                    connection.getSelfId(),
                    connection.getOtherId());
            return;
        }

        try {
            connection.setTimeout(originalSocketTimeout);
        } catch (final SocketException e) {
            throw new ReconnectException(e);
        }
    }

    /**
     * Perform the reconnect operation.
     *
     * @throws ReconnectException thrown when current thread is interrupted, or when any I/O related
     *     errors occur, or when there is an error in the underlying protocol
     */
    public void execute() throws ReconnectException {
        try {
            executeInternal();
        } finally {
            if (!stateIsReleased) {
                // If execution aborted without finishing the reconnect, ensure the state is
                // properly released
                signedState.weakReleaseState();
            }
        }
    }

    private void executeInternal() {
        // If the connection object to be used here has been disconnected on another thread, we can
        // not reconnect with this connection.
        if (!connection.connected()) {
            logger.debug(
                    RECONNECT.getMarker(),
                    "{} connection to {} is no longer connected. Returning.",
                    connection.getSelfId(),
                    connection.getOtherId());
            return;
        }
        logReconnectStart();
        increaseSocketTimeout();

        try {
            sendSignatures();
            reconnect();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReconnectException(e);
        } catch (final IOException e) {
            throw new ReconnectException(e);
        } finally {
            resetSocketTimeout();
        }
        logReconnectFinish();
    }

    private void logReconnectStart() {
        logger.info(
                RECONNECT.getMarker(),
                () ->
                        new ReconnectStartPayload(
                                "Starting reconnect in the role of the sender",
                                false,
                                selfId,
                                otherId,
                                lastRoundReceived));
        logger.info(
                RECONNECT.getMarker(),
                "The following state will be sent to the learner:\n{}\n{}",
                () -> signedState.getState().getPlatformState().getInfoString(),
                () ->
                        generateHashDebugString(
                                signedState.getState(), StateSettings.getDebugHashDepth()));
    }

    private void logReconnectFinish() {
        logger.info(
                RECONNECT.getMarker(),
                () ->
                        new ReconnectFinishPayload(
                                "Finished reconnect in the role of the sender.",
                                false,
                                selfId,
                                otherId,
                                lastRoundReceived));
    }

    /**
     * Copy the signed state from this node to the other node.
     *
     * @throws InterruptedException thrown if the current thread is interrupted
     */
    private void reconnect() throws InterruptedException, IOException {
        logger.info(RECONNECT.getMarker(), "Starting synchronization in the role of the sender.");
        statistics.incrementSenderStartTimes();

        connection.getDis().getSyncByteCounter().resetCount();
        connection.getDos().getSyncByteCounter().resetCount();

        final TeachingSynchronizer synchronizer =
                new TeachingSynchronizer(
                        threadManager,
                        new MerkleDataInputStream(connection.getDis()),
                        new MerkleDataOutputStream(connection.getDos()),
                        signedState.getState(),
                        connection::disconnect);

        // State is acquired via SignedStateManager.getLastCompleteSignedState(), which acquires
        // a weak reservation. The synchronizer manually acquires references to merkle nodes in the
        // state within its constructor. After that has completed, the lock on the signed state
        // itself
        // is no longer required to be held.
        stateIsReleased = true;
        signedState.weakReleaseState();

        synchronizer.synchronize();
        connection.getDos().flush();

        statistics.incrementSenderEndTimes();
        logger.info(RECONNECT.getMarker(), "Finished synchronization in the role of the sender.");
    }

    /**
     * Copy the signatures on the signed state from this node to the other node.
     *
     * @throws IOException thrown when any I/O related errors occur
     */
    private void sendSignatures() throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("Sending signatures from nodes ");
        formattedList(sb, signedState.getSigSet().iterator());
        sb.append(" (signing stake = ")
                .append(signedState.getSigningStake())
                .append("/")
                .append(signedState.getAddressBook().getTotalStake())
                .append(") for state hash ")
                .append(signedState.getState().getHash());

        logger.info(RECONNECT.getMarker(), sb);
        connection.getDos().writeSerializable(signedState.getSigSet(), true);
        connection.getDos().flush();
    }
}

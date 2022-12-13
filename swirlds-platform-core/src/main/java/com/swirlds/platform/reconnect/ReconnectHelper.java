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
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.utility.Clearable;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectLoadFailurePayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.platform.Connection;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignedState;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A single point responsible for executing the 3 phases of reconnect:
 *
 * <ol>
 *   <li>Preparation - stop gossip and clear all data currently being processed
 *   <li>Receive state - execute the reconnect protocol with a neighbor to receive a signed state
 *   <li>Load data - load the data from a signed state into various parts of the system
 * </ol>
 */
public class ReconnectHelper {
    private static final Logger LOG = LogManager.getLogger();

    /** stops all gossiping */
    private final Runnable stopGossip;
    /** clears all data that is no longer needed since we fell behind */
    private final Clearable clearAll;
    /** supplier of the initial signed state against which to perform a delta based reconnect */
    private final Supplier<State> workingStateSupplier;
    /** provides the latest signed state round for which we have a supermajority of signatures */
    private final LongSupplier lastCompleteRoundSupplier;
    /** throttles reconnect learner attempts */
    private final ReconnectLearnerThrottle reconnectLearnerThrottle;
    /** performs the third phase, loading signed state data */
    private final Consumer<SignedState> loadSignedState;
    /**
     * Creates instances of {@link ReconnectLearner} to execute the second phase, receiving a signed
     * state
     */
    private final ReconnectLearnerFactory reconnectLearnerFactory;

    public ReconnectHelper(
            final Runnable stopGossip,
            final Clearable clearAll,
            final Supplier<State> workingStateSupplier,
            final LongSupplier lastCompleteRoundSupplier,
            final ReconnectLearnerThrottle reconnectLearnerThrottle,
            final Consumer<SignedState> loadSignedState,
            final ReconnectLearnerFactory reconnectLearnerFactory) {
        this.stopGossip = stopGossip;
        this.clearAll = clearAll;
        this.workingStateSupplier = workingStateSupplier;
        this.lastCompleteRoundSupplier = lastCompleteRoundSupplier;
        this.reconnectLearnerThrottle = reconnectLearnerThrottle;
        this.loadSignedState = loadSignedState;
        this.reconnectLearnerFactory = reconnectLearnerFactory;
    }

    /**
     * Performs necessary operations before a reconnect such as stopping threads, clearing queues,
     * etc.
     */
    public void prepareForReconnect() {
        reconnectLearnerThrottle.exitIfReconnectIsDisabled();
        LOG.info(RECONNECT.getMarker(), "Preparing for reconnect, stopping gossip");
        stopGossip.run();
        LOG.info(RECONNECT.getMarker(), "Preparing for reconnect, start clearing queues");
        clearAll.clear();
        LOG.info(RECONNECT.getMarker(), "Queues have been cleared");
        // Hash the state if it has not yet been hashed
        ReconnectUtils.hashStateForReconnect(workingStateSupplier.get());
    }

    /**
     * Attempts to receive a new signed state by reconnecting with the specified neighbor.
     *
     * @param conn the connection to use for the reconnect attempt
     * @return the signed state received from the neighbor
     * @throws ReconnectException if any error occurs during the reconnect attempt
     */
    public SignedState receiveSignedState(
            final Connection conn, final SignedStateValidator validator) throws ReconnectException {
        final SignedState signedState;
        try {
            signedState = reconnectLearner(conn, validator);
            reconnectLearnerThrottle.successfulReconnect();

            LOG.debug(
                    RECONNECT.getMarker(),
                    "`doReconnect` : finished, found peer node {}",
                    conn.getOtherId());
            return signedState;
        } catch (final RuntimeException e) {
            reconnectLearnerThrottle.handleFailedReconnect(conn, e);
            throw e;
        }
    }

    private SignedState reconnectLearner(
            final Connection conn, final SignedStateValidator validator) throws ReconnectException {
        final SignedState signedState;

        LOG.info(
                RECONNECT.getMarker(),
                () ->
                        new ReconnectStartPayload(
                                        "Starting reconnect in role of the receiver.",
                                        true,
                                        conn.getSelfId().getIdAsInt(),
                                        conn.getOtherId().getIdAsInt(),
                                        lastCompleteRoundSupplier.getAsLong())
                                .toString());

        final ReconnectLearner reconnect =
                reconnectLearnerFactory.create(conn, workingStateSupplier.get());

        reconnect.execute(validator);

        signedState = reconnect.getSignedState();
        final long lastRoundReceived = signedState.getRound();

        LOG.info(
                RECONNECT.getMarker(),
                () ->
                        new ReconnectFinishPayload(
                                        "Finished reconnect in the role of the receiver.",
                                        true,
                                        conn.getSelfId().getIdAsInt(),
                                        conn.getOtherId().getIdAsInt(),
                                        lastRoundReceived)
                                .toString());

        LOG.info(
                RECONNECT.getMarker(),
                "Information for state received during reconnect:\n{}\n{}",
                () -> signedState.getState().getPlatformState().getInfoString(),
                () ->
                        generateHashDebugString(
                                signedState.getState(), StateSettings.getDebugHashDepth()));

        LOG.info(
                RECONNECT.getMarker(),
                "signed state events:\n{}",
                () -> EventUtils.toShortStrings(signedState.getEvents()));

        return signedState;
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     * @return true if the state was successfully loaded; otherwise false
     */
    public boolean loadSignedState(final SignedState signedState) {
        try {
            loadSignedState.accept(signedState);
        } catch (final RuntimeException e) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    () ->
                            new ReconnectLoadFailurePayload(
                                            "Error while loading a received SignedState!")
                                    .toString(),
                    e);
            // this means we need to start the reconnect process from the beginning
            LOG.debug(
                    RECONNECT.getMarker(),
                    "`reloadState` : reloading state, finished, failed, returning `false`: Restart"
                            + " the reconnection process");
            reconnectLearnerThrottle.handleFailedReconnect(null, e);
            return false;
        }
        return true;
    }
}

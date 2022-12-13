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
package com.swirlds.platform.recovery;

import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.system.SystemExitReason.SAVED_STATE_NOT_LOADED;
import static com.swirlds.platform.system.SystemUtils.exitSystem;

import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import com.swirlds.logging.payloads.RecoveredStateSavedPayload;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.util.router.BinaryRouter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Performs state recovery in the event of a node crashing. The last signed state saved on disk is
 * loaded and events from the event stream are replayed to get the latest signed state. Once the
 * signed state for the last complete round is written to disk, the node shuts down.
 */
public class StateRecovery {

    private static final Logger LOG = LogManager.getLogger();
    private static final long WAIT_SEC_FOR_EVENT_FLUSH = 10;
    private final NodeId selfId;
    private final SettingsProvider settings;
    private final String addressMemo;
    private final SignedState signedState;
    private final EventStreamManager<EventImpl> eventStreamManager;
    private final SignedStateFileManager signedStateFileManager;
    private final ConsensusRoundHandler consensusRoundHandler;
    /** The start of the time range of consensus events to recover */
    private final Instant startTimestamp;
    /** The end of the time range of consensus events to recover */
    private final Instant endTimestamp;
    /** The last complete recovered round */
    private long lastCompleteRound = Long.MAX_VALUE;

    /**
     * @param selfId this node's id
     * @param settings a provider of static settings
     * @param addressMemo this node's address memo
     * @param signedState the latest signed state on disk
     * @param eventStreamManager the event stream manager
     * @param signedStateFileManager the signed state file manager
     * @param consensusRoundHandler the consensus round handler
     */
    public StateRecovery(
            final NodeId selfId,
            final SettingsProvider settings,
            final String addressMemo,
            final SignedState signedState,
            final EventStreamManager<EventImpl> eventStreamManager,
            final SignedStateFileManager signedStateFileManager,
            final ConsensusRoundHandler consensusRoundHandler) {

        validateSignedState(signedState);
        this.selfId = selfId;
        this.settings = settings;
        this.addressMemo = addressMemo;
        this.signedState = signedState;
        this.eventStreamManager = eventStreamManager;
        this.signedStateFileManager = signedStateFileManager;
        this.consensusRoundHandler = consensusRoundHandler;

        startTimestamp = signedState.getConsensusTimestamp();
        endTimestamp = getEndTimestamp();
    }

    private static void validateSignedState(final SignedState signedState) {
        if (signedState == null) {
            LOG.error(EXCEPTION.getMarker(), "No saved state can be used for recover process");
            exitSystem(SAVED_STATE_NOT_LOADED);
        }
    }

    private Path getEventStreamDir() {
        final String name;
        if (addressMemo != null && !addressMemo.isEmpty()) {
            name = addressMemo;
        } else {
            name = String.valueOf(selfId);
        }
        return Path.of(settings.getPlaybackStreamFileDirectory(), "/events_" + name);
    }

    /**
     * Recover events from the event stream starting from the last saved state on disk and apply
     * them to the state. Save the last saved state to disk before shutting down.
     */
    public void execute() {
        LOG.info(
                EVENT_PARSER.getMarker(),
                "State recover process started. Last timestamp from loaded state is {} of round {}",
                startTimestamp,
                signedState.getRound());

        final EventStreamParser parser =
                new EventStreamParser(
                        getEventStreamDir(),
                        startTimestamp,
                        endTimestamp,
                        eventStreamManager::setInitialHash);

        final RecoveredEventAggregator aggregator =
                new RecoveredEventAggregator(
                        consensusRoundHandler::addConsensusRound,
                        consensusRoundHandler::addMinGenInfo);

        final BinaryRouter<EventImpl> eventRouter =
                new BinaryRouter<>(
                        this::eventInTimeRange,
                        aggregator::addEvent,
                        eventStreamManager::addEvent,
                        parser::hasMoreEvents,
                        () -> parser.getNextEvent(10, TimeUnit.MILLISECONDS));

        // On a separate thread, parse event files and put the parsed events into a queue
        parser.start();

        // On this thread, poll the event queue and route all events to the event
        // stream or event aggregator. Continue polling and routing until all events have been
        // routed.
        // Parsing events and applying those events to the state are done simultaneously to avoid
        // running out of memory
        // if a large number of events must be recovered.
        eventRouter.route();

        // No more events will be routed, so we can now determine the last complete round that will
        // be sent to the
        // consensus round handler. Store the last complete round number so that we know to save it
        // to disk regardless
        // of the state saving settings.
        lastCompleteRound = aggregator.noMoreEvents();

        // Register a shutdown hook for the last signed state written to disk
        registerShutdownListener();

        // send the last round
        aggregator.sendLastRound();

        final long totalRouted = eventRouter.getNumPassed() + eventRouter.getNumFailed();
        if (totalRouted != parser.getEventCounter()) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "Error : Number of events parsed {} not equal to the number routed {}",
                    parser.getEventCounter(),
                    totalRouted);
        }

        LOG.info(EVENT_PARSER.getMarker(), "Parsed {} events", parser.getEventCounter());
        LOG.info(EVENT_PARSER.getMarker(), "Routed {} events", totalRouted);
        LOG.info(
                EVENT_PARSER.getMarker(),
                "Sent {} events to be aggregated into rounds",
                eventRouter.getNumPassed());
        LOG.info(
                EVENT_PARSER.getMarker(),
                "Sent {} events directly to the event stream",
                eventRouter.getNumFailed());
        LOG.info(
                EVENT_PARSER.getMarker(),
                "Inserted {} events ({} rounds) to consensus round queue",
                aggregator.getTotalEvents(),
                aggregator.getTotalRounds());
        LOG.info(
                EVENT_PARSER.getMarker(),
                "roundOfLastRecoveredEvent {}",
                aggregator.getLastCompleteRound());

        if (parser.getEventCounter() == 0) {
            LOG.info(EVENT_PARSER.getMarker(), "No event parsed from event files");
            exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
        }
    }

    private void registerShutdownListener() {
        NotificationFactory.getEngine()
                .register(
                        StateWriteToDiskCompleteListener.class,
                        notification -> {
                            // in recover mode, once we saved the last recovered signed state we can
                            // exit recover mode
                            if (notification.getRoundNumber() >= lastCompleteRound) {
                                LOG.info(
                                        EVENT_PARSER.getMarker(),
                                        () ->
                                                new RecoveredStateSavedPayload(
                                                                "Last recovered signed state has"
                                                                    + " been saved in state recover"
                                                                    + " mode.",
                                                                notification.getRoundNumber())
                                                        .toString());
                                // sleep 10 secs to let event stream finish writing the last file
                                try {
                                    TimeUnit.SECONDS.sleep(WAIT_SEC_FOR_EVENT_FLUSH);
                                } catch (final InterruptedException e) {
                                    LOG.error(EXCEPTION.getMarker(), "could not sleep", e);
                                    Thread.currentThread().interrupt();
                                }
                                exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
                            }
                        });
    }

    /**
     * This method is called when a signed state is constructed during recovery.
     *
     * @param signedState a signed state that was just constructed, may not be fully signed
     */
    public void newSignedStateInRecovery(final SignedState signedState) {
        final NewSignedStateNotification notification =
                new NewSignedStateNotification(
                        signedState.getSwirldState(),
                        signedState.getState().getSwirldDualState(),
                        signedState.getRound(),
                        signedState.getConsensusTimestamp());
        signedState.reserveState();
        NotificationFactory.getEngine()
                .dispatch(
                        NewSignedStateListener.class,
                        notification,
                        result -> signedState.releaseState());

        // save the last recovered state, even state save period requirement is not met
        if (signedState.getRound() >= lastCompleteRound) {
            signedStateFileManager.saveSignedStateToDisk(signedState);
        }
    }

    private boolean eventInTimeRange(final EventImpl event) {
        final Instant consTime = event.getConsensusTimestamp();
        return startTimestamp.isBefore(consTime) && !consTime.isAfter(endTimestamp);
    }

    private Instant getEndTimestamp() {
        Instant endTimeStamp = Instant.MAX;
        if (!settings.getPlaybackEndTimeStamp().isEmpty()) {
            try {
                endTimeStamp = Instant.parse(settings.getPlaybackEndTimeStamp());
            } catch (final DateTimeParseException e) {
                LOG.info(EXCEPTION.getMarker(), "Parsing playbackEndTimeStamp error ", e);
            }
        }
        return endTimeStamp;
    }
}

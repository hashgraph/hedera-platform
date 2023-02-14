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

import static com.swirlds.logging.LogMarker.ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.FREEZE;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.SystemTransactionHandler;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.connectivity.TcpFactory;
import com.swirlds.platform.network.connectivity.TlsFactory;
import com.swirlds.platform.state.SignatureTransmitter;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import com.swirlds.platform.state.notifications.NewSignedStateBeingTrackedListener;
import com.swirlds.platform.state.notifications.StateHasEnoughSignaturesListener;
import com.swirlds.platform.state.notifications.StateLacksSignaturesListener;
import com.swirlds.platform.state.notifications.StateSelfSignedListener;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.system.Fatal;
import com.swirlds.platform.system.PlatformConstructionException;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import com.swirlds.platform.util.HashLogger;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Used to construct platform components that use DI */
final class PlatformConstructor {

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger LOG = LogManager.getLogger(PlatformConstructor.class);

    /**
     * The maximum size of the queue holding signed states ready to be hashed and signed by others.
     */
    private static final int STATE_HASH_QUEUE_MAX = 1;

    /** Private constructor so that this class is never instantiated */
    private PlatformConstructor() {}

    /**
     * Create a parallel executor.
     *
     * @param threadManager responsible for managing thread lifecycles
     */
    static ParallelExecutor parallelExecutor(final ThreadManager threadManager) {
        return new CachedPoolParallelExecutor(threadManager, "node-sync");
    }

    static SettingsProvider settingsProvider() {
        return StaticSettingsProvider.getSingleton();
    }

    static SocketFactory socketFactory(
            final KeysAndCerts keysAndCerts, final CryptoConfig cryptoConfig) {
        if (!Settings.getInstance().isUseTLS()) {
            return new TcpFactory(PlatformConstructor.settingsProvider());
        }
        try {
            return new TlsFactory(
                    keysAndCerts, PlatformConstructor.settingsProvider(), cryptoConfig);
        } catch (final NoSuchAlgorithmException
                | UnrecoverableKeyException
                | KeyStoreException
                | KeyManagementException
                | CertificateException
                | IOException e) {
            throw new PlatformConstructionException(
                    "A problem occurred while creating the SocketFactory", e);
        }
    }

    static PlatformSigner platformSigner(final KeysAndCerts keysAndCerts) {
        try {
            return new PlatformSigner(keysAndCerts);
        } catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
            throw new PlatformConstructionException(e);
        }
    }

    /**
     * Creates the {@link QueueThread} that stores and handles signed states that need to be hashed
     * and have signatures collected.
     *
     * @param threadManager responsible for managing thread lifecycles
     * @param selfId this node's id
     * @param signedStateManager the signed state manager that collects signatures
     */
    static QueueThread<SignedState> stateHashSignQueue(
            final ThreadManager threadManager,
            final long selfId,
            final SignedStateManager signedStateManager) {

        return new QueueThreadConfiguration<SignedState>(threadManager)
                .setNodeId(selfId)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("state-hash-sign")
                .setHandler(signedStateManager::addUnsignedState)
                .setCapacity(STATE_HASH_QUEUE_MAX)
                .build();
    }

    /**
     * Creates a new instance of {@link SwirldStateManager}.
     *
     * @param threadManager responsible for creating and managing threads
     * @param selfId this node's id
     * @param systemTransactionHandler the handler of system transactions
     * @param metrics reference to the metrics-system
     * @param settings static settings provider
     * @param consEstimateSupplier supplier of an estimated consensus time for transactions
     * @param initialState the initial state
     * @return the newly constructed instance of {@link SwirldStateManager}
     */
    public static SwirldStateManager swirldStateManager(
            final ThreadManager threadManager,
            final NodeId selfId,
            final SystemTransactionHandler systemTransactionHandler,
            final Metrics metrics,
            final SettingsProvider settings,
            final Supplier<Instant> consEstimateSupplier,
            final BooleanSupplier inFreezeChecker,
            final State initialState) {

        if (initialState.getSwirldState() instanceof SwirldState2) {
            return new SwirldStateManagerDouble(
                    selfId,
                    systemTransactionHandler,
                    new SwirldStateMetrics(metrics),
                    settings,
                    inFreezeChecker,
                    initialState);
        } else if (initialState.getSwirldState() instanceof SwirldState1) {
            return new SwirldStateManagerSingle(
                    threadManager,
                    selfId,
                    systemTransactionHandler,
                    new SwirldStateMetrics(metrics),
                    new ConsensusMetricsImpl(selfId, metrics),
                    settings,
                    consEstimateSupplier,
                    inFreezeChecker,
                    initialState);
        } else {
            LOG.error(
                    ERROR.getMarker(),
                    "Unrecognized SwirldState class: {}",
                    initialState.getClass());
            SystemUtils.exitSystem(SystemExitReason.FATAL_ERROR);
            return null;
        }
    }

    /**
     * Constructs a new {@link PreConsensusEventHandler}.
     *
     * @param threadManager responsible for creating and managing threads
     * @param selfId this node's id
     * @param swirldStateManager the instance of {@link SwirldStateManager}
     * @param consensusMetrics the class that records stats relating to {@link SwirldStateManager}
     * @return the newly constructed instance of {@link PreConsensusEventHandler}
     */
    public static PreConsensusEventHandler preConsensusEventHandler(
            final ThreadManager threadManager,
            final NodeId selfId,
            final SwirldStateManager swirldStateManager,
            final ConsensusMetrics consensusMetrics) {

        return new PreConsensusEventHandler(
                threadManager, selfId, swirldStateManager, consensusMetrics);
    }

    /**
     * Constructs a new {@link ConsensusRoundHandler}.
     *
     * @param threadManager responsible for creating and managing threads
     * @param dispatchBuilder responsible for building dispatchers
     * @param selfId this node's id
     * @param settingsProvider a static settings provider
     * @param swirldStateManager the instance of {@link SwirldStateManager}
     * @param consensusHandlingMetrics the class that records stats relating to {@link
     *     SwirldStateManager}
     * @param eventStreamManager the instance that streams consensus events to disk
     * @param addressBook the address book of the network
     * @param stateHashSignQueue the queue for signed states that need signatures collected
     * @param enterFreezePeriod a runnable executed when a freeze is entered
     * @param softwareVersion the software version of the application
     * @return the newly constructed instance of {@link ConsensusRoundHandler}
     */
    public static ConsensusRoundHandler consensusHandler(
            final PlatformContext platformContext,
            final ThreadManager threadManager,
            final DispatchBuilder dispatchBuilder,
            final long selfId,
            final SettingsProvider settingsProvider,
            final SwirldStateManager swirldStateManager,
            final ConsensusHandlingMetrics consensusHandlingMetrics,
            final EventStreamManager<EventImpl> eventStreamManager,
            final AddressBook addressBook,
            final BlockingQueue<SignedState> stateHashSignQueue,
            final Runnable enterFreezePeriod,
            final SoftwareVersion softwareVersion) {

        return new ConsensusRoundHandler(
                platformContext,
                threadManager,
                dispatchBuilder,
                selfId,
                settingsProvider,
                swirldStateManager,
                consensusHandlingMetrics,
                eventStreamManager,
                addressBook,
                stateHashSignQueue,
                enterFreezePeriod,
                softwareVersion);
    }

    /**
     * Register a listener for fatal events, i.e. unrecoverable errors that force the node to shut
     * down.
     */
    public static void registerFatalListener(
            final NotificationEngine notificationEngine,
            final SignedStateManager signedStateManager,
            final SignedStateFileManager signedStateFileManager) {

        notificationEngine.register(
                Fatal.FatalListener.class,
                (Fatal.FatalNotification notification) -> {
                    if (Settings.getInstance().getState().dumpStateOnFatal) {
                        try (final AutoCloseableWrapper<SignedState> wrapper =
                                signedStateManager.getLatestSignedState(false)) {
                            final SignedState state = wrapper.get();
                            if (state != null) {
                                signedStateFileManager.dumpState(state, "fatal", true);
                            }
                        }
                    }
                });
    }

    /** Register a listener for when this node self-signs a state. */
    public static void registerStateSelfSignedListener(
            final SwirldsPlatform platform, final NodeId selfId) {

        final NetworkMetrics networkMetrics = new NetworkMetrics(platform);

        platform.getNotificationEngine()
                .register(
                        StateSelfSignedListener.class,
                        notification -> {
                            if (notification.getSelfId().equals(selfId)) {
                                SignatureTransmitter.transmitSignature(
                                        platform,
                                        notification.getRound(),
                                        notification.getSelfSignature(),
                                        notification.getStateHash());
                                NetworkStatsTransmitter.transmitStats(platform, networkMetrics);
                            }
                        });
    }

    /** Register a listener for when a signed state gathers enough signatures to become complete. */
    public static void registerStateHasEnoughSignaturesListener(
            final NotificationEngine notificationEngine,
            final NodeId selfId,
            final FreezeManager freezeManager,
            final SignedStateFileManager signedStateFileManager) {

        notificationEngine.register(
                StateHasEnoughSignaturesListener.class,
                notification -> {
                    if (notification.getSelfId().equals(selfId)) {
                        if (notification.getSignedState().isFreezeState()) {
                            LOG.info(
                                    FREEZE.getMarker(),
                                    "Collected enough signatures on the freeze state (round = {}). "
                                            + "Freezing event creation now.",
                                    notification.getSignedState().getRound());
                            freezeManager.freezeEventCreation();
                        }
                        if (notification.getSignedState().isStateToSave()) {
                            signedStateFileManager.saveSignedStateToDisk(
                                    notification.getSignedState());
                        }
                    }
                });
    }

    /**
     * Register a listener for when a signed state fails to collect enough signatures to be complete
     * before aging out and being discarded.
     */
    public static void registerStateLacksSignaturesListener(
            final NotificationEngine notificationEngine,
            final NodeId selfId,
            final FreezeManager freezeManager,
            final SignedStateMetrics signedStateMetrics,
            final SignedStateFileManager signedStateFileManager) {

        notificationEngine.register(
                StateLacksSignaturesListener.class,
                notification -> {
                    if (notification.getSelfId().equals(selfId)) {
                        if (notification.getSignedState().isFreezeState()) {
                            LOG.error(
                                    FREEZE.getMarker(),
                                    "Unable to collect enough signatures on the freeze state (round"
                                            + " = {}). THIS SHOULD NEVER HAPPEN! This node may not"
                                            + " start from the same state as other nodes after a"
                                            + " restart. Freezing event creation anyways.",
                                    notification.getSignedState().getRound());
                            freezeManager.freezeEventCreation();
                        }
                        if (notification.getSignedState().isStateToSave()) {
                            signedStateMetrics.getTotalUnsignedDiskStatesMetric().increment();
                            LOG.error(
                                    EXCEPTION.getMarker(),
                                    "state written to disk for round {} did not have enough"
                                            + " signatures",
                                    notification.getSignedState().getRound());
                            signedStateFileManager.saveSignedStateToDisk(
                                    notification.getSignedState());
                        }
                    }
                });
    }

    /** Register a listener for when the signed state manager starts tracking a new signed state. */
    public static void registerNewSignedStateBeingTrackedListener(
            final NotificationEngine notificationEngine,
            final NodeId selfId,
            final SignedStateFileManager signedStateFileManager,
            final HashLogger hashLogger) {

        // When we begin tracking a new signed state, "introduce" the state to the
        // SignedStateFileManager
        notificationEngine.register(
                NewSignedStateBeingTrackedListener.class,
                notification -> {
                    if (notification.getSelfId().equals(selfId)) {
                        if (notification.getSourceOfSignedState() == SourceOfSignedState.DISK) {
                            signedStateFileManager.registerSignedStateFromDisk(
                                    notification.getSignedState());
                        } else {
                            signedStateFileManager.determineIfStateShouldBeSaved(
                                    notification.getSignedState());
                        }
                        if (notification.getSignedState().getState().getHash() != null) {
                            hashLogger.logHashes(notification.getSignedState());
                        }
                    }
                });
    }
}

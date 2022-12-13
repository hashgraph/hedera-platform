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

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.system.SystemExitReason.SWIRLD_MAIN_THREW_EXCEPTION;

import com.swirlds.common.Console;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.MetricsWriterService;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.threading.wrappers.SequenceCycle;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Clearables;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.logging.LogMarker;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.chatter.ChatterNotifier;
import com.swirlds.platform.chatter.ChatterSyncProtocol;
import com.swirlds.platform.chatter.PrepareChatterEvent;
import com.swirlds.platform.chatter.communication.ChatterProtocol;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.CriticalQuorumImpl;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.EventCreator;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.EventMapper;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.components.EventTaskDispatcher;
import com.swirlds.platform.components.StartupThrottle;
import com.swirlds.platform.components.SwirldMainManager;
import com.swirlds.platform.components.SystemTransactionHandlerImpl;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRules;
import com.swirlds.platform.components.TransactionTracker;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.event.EventCreatorThread;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.event.creation.BelowIntCreationRule;
import com.swirlds.platform.event.creation.ChatterEventCreator;
import com.swirlds.platform.event.creation.ChatteringRule;
import com.swirlds.platform.event.creation.LoggingEventCreationRules;
import com.swirlds.platform.event.creation.OtherParentTracker;
import com.swirlds.platform.event.creation.StaticCreationRules;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.event.validation.AncientValidator;
import com.swirlds.platform.event.validation.EventDeduplication;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.SignatureValidator;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.event.validation.ZeroStakeValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.intake.IntakeCycleStats;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.ConsensusMetricsImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.metrics.RuntimeMetrics;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.metrics.TransactionMetrics;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkMetrics;
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorThread;
import com.swirlds.platform.network.communication.handshake.VersionCompareHandshake;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.network.connectivity.OutboundConnectionCreator;
import com.swirlds.platform.network.connectivity.SocketFactory;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.network.topology.StaticConnectionManagers;
import com.swirlds.platform.network.topology.StaticTopology;
import com.swirlds.platform.network.unidirectional.HeartbeatProtocolResponder;
import com.swirlds.platform.network.unidirectional.HeartbeatSender;
import com.swirlds.platform.network.unidirectional.Listener;
import com.swirlds.platform.network.unidirectional.MultiProtocolResponder;
import com.swirlds.platform.network.unidirectional.ProtocolMapping;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.network.unidirectional.UnidirectionalProtocols;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.reconnect.DefaultSignedStateValidator;
import com.swirlds.platform.reconnect.FallenBehindManagerImpl;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectProtocolResponder;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.emergency.EmergencyReconnectProtocol;
import com.swirlds.platform.recovery.StateRecovery;
import com.swirlds.platform.state.BackgroundHashChecker;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphEventObserver;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SimultaneousSyncThrottle;
import com.swirlds.platform.sync.SyncProtocolResponder;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.threading.PauseAndClear;
import com.swirlds.platform.threading.PauseAndLoad;
import com.swirlds.platform.util.HashLogger;
import com.swirlds.platform.util.PlatformComponents;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SwirldsPlatform implements Platform, SwirldMainManager, ConnectionTracker {

    public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger LOG = LogManager.getLogger();
    /** alert threshold for java app pause */
    private static final long PAUSE_ALERT_INTERVAL = 5000;
    /** logging string prefix for hash stream operation logged events. */
    private static final String HASH_STREAM_OPERATION_PREFIX = ">>> ";
    /**
     * the ID of the member running this. Since a node can be a main node or a mirror node, the ID
     * is not a primitive value
     */
    protected final NodeId selfId;
    // The following hold info about the swirld being run (appMain), and the platform's id
    /** tell which pairs of members should establish connections */
    final NetworkTopology topology;
    /** This object is responsible for rate limiting reconnect attempts (in the role of sender) */
    private final ReconnectThrottle reconnectThrottle;

    private final Settings settings = Settings.getInstance();
    /**
     * A type used by Hashgraph, Statistics, and SyncUtils. Only Hashgraph modifies this type
     * instance.
     */
    private final EventMapper eventMapper;
    /**
     * A simpler event mapper used for chatter. Stores the thread-safe GossipEvent and has less
     * functionality. The plan is for this to replace EventMapper once syncing is removed from the
     * code
     */
    private final ChatterEventMapper chatterEventMapper;
    /** the name of the swirld being run */
    private final String swirldName;
    /** the name of the main class this platform will be running */
    private final String mainClassName;
    /** this is the Nth Platform running on this machine (N=winNum) */
    private final int winNum;
    /** parameters given to the app when it starts */
    private final String[] parameters;
    /** various signed states in various stages of collecting signatures */
    private final SignedStateManager signedStateManager;
    /** Handles all system transactions */
    private final SystemTransactionHandlerImpl systemTransactionHandler;
    /** The platforms freeze manager */
    private final FreezeManager freezeManager;
    /** is used for pausing event creation for a while at start up */
    private final StartUpEventFrozenManager startUpEventFrozenManager;
    /**
     * The shadow graph manager. This wraps a shadow graph, which is an Event graph that adds child
     * pointers to the Hashgraph Event graph. Used for gossiping.
     */
    private final ShadowGraph shadowGraph;
    /** The last status of the platform that was determined, is null until the platform starts up */
    private final AtomicReference<PlatformStatus> currentPlatformStatus =
            new AtomicReference<>(null);
    /** the number of active connections this node has to other nodes */
    private final AtomicInteger activeConnectionNumber = new AtomicInteger(0);
    /**
     * the object used to calculate consensus. it is volatile because the whole object is replaced
     * when reading a state from disk or getting it through reconnect
     */
    private final AtomicReference<Consensus> consensusRef;
    /** set in the constructor and given to the SwirldState object in run() */
    private final AddressBook initialAddressBook;

    private final Metrics metrics;
    private final AddedEventMetrics addedEventMetrics;
    private final PlatformMetrics platformMetrics;

    private final SimultaneousSyncThrottle simultaneousSyncThrottle;
    private final ConsensusMetrics consensusMetrics;
    private final EventIntakeMetrics eventIntakeMetrics;
    private final SignedStateMetrics signedStateMetrics;
    private final SyncMetrics syncMetrics;
    private final NetworkMetrics networkMetrics;
    private final ReconnectMetrics reconnectMetrics;
    /** logger for hash stream data */
    private final HashLogger hashLogger;
    /** Reference to the instance responsible for executing reconnect when chatter is used */
    private final AtomicReference<ReconnectController> reconnectController =
            new AtomicReference<>();
    /** tracks if we have fallen behind or not, takes appropriate action if we have */
    private final FallenBehindManagerImpl fallenBehindManager;
    /** stats related to the intake cycle */
    private final IntakeCycleStats intakeCycleStats;

    private final ChatterCore<GossipEvent> chatterCore;
    /** all the events and other data about the hashgraph */
    protected EventTaskCreator eventTaskCreator;
    /** ID number of the swirld being run */
    protected byte[] swirldId;
    /** the object that contains all key pairs and CSPRNG state for this member */
    protected Crypto crypto;
    /** Set to true when state recovery is in progress. */
    protected volatile boolean stateRecoveryInProgress;
    /** the main program for the app. (The app also has a SwirldState, managed by eventFlow) */
    private SwirldMain appMain;
    /** a long name including (app, swirld, member id, member self name) */
    private String platformName;
    /**
     * this Platform's infoMember, representing the (app, swirld ID, member ID) triplet it is
     * running
     */
    private StateHierarchy.InfoMember infoMember;
    /**
     * is used for calculating runningHash of all consensus events and writing consensus events to
     * file
     */
    private EventStreamManager<EventImpl> eventStreamManager;
    /** the initial signed state that was loaded from disk on platform startup */
    private volatile SignedState signedState = null;
    /**
     * The initial swirld state that was loaded from disk on platform startup, this gets passed to
     * {@link SwirldStateManager}. Should only be accessed in synchronized blocks.
     */
    private State initialState = null;
    /**
     * The previous version of the software that was run. Null if this is the first time running, or
     * if the previous version ran before the concept of application software versioning was
     * introduced.
     */
    private SoftwareVersion previousSoftwareVersion;
    /** Helps when executing a reconnect */
    private ReconnectHelper reconnectHelper;
    /** tells callers who to sync with and keeps track of whether we have fallen behind */
    private SyncManagerImpl syncManager;
    /** locks used to synchronize usage of outbound connections */
    private SharedConnectionLocks sharedConnectionLocks;
    /** a thread that writes, reads and deletes FastCopyable object to/from files */
    private final SignedStateFileManager signedStateFileManager;
    /** last time stamp when pause check timer is active */
    private long pauseCheckTimeStamp;
    /** the round number of last recover state with valid new user transactions */
    private long roundOfLastRecoveredEvent;
    /** the app can set appAbout, which shows in the menu's "about" */
    private String appAbout = "";
    /** allows nodes to wait for each other when starting up */
    private StartupThrottle startupThrottle;
    /** Tracks user transactions in the hashgraph */
    private TransactionTracker transactionTracker;
    /** Tracks recent events created in the network */
    private CriticalQuorum criticalQuorum;

    private QueueThread<EventIntakeTask> intakeQueue;
    private EventLinker eventLinker;
    private SequenceCycle<EventIntakeTask> intakeCycle = null;
    /** sleep in ms after each sync in SyncCaller. A public setter for this exists. */
    private long delayAfterSync = 0;
    /**
     * freezeTime in seconds; when the node is started from genesis, and this value is positive, set
     * this node's freezeTime to be an Instant with this many epoch seconds
     */
    private long genesisFreezeTime = -1;
    /** Executes a sync with a remote node */
    private ShadowGraphSynchronizer shadowgraphSynchronizer;
    /** Stores and passes pre-consensus events to {@link SwirldStateManager} for handling */
    private PreConsensusEventHandler preConsensusEventHandler;
    /**
     * Stores and processes consensus events including sending them to {@link SwirldStateManager}
     * for handling
     */
    private ConsensusRoundHandler consensusRoundHandler;
    /** Handles all interaction with {@link SwirldState} */
    private SwirldStateManager swirldStateManager;
    /** Checks the validity of transactions and submits valid ones to the event transaction pool */
    private TransactionSubmitter transactionSubmitter;
    /** clears all pipelines to prepare for a reconnect */
    private Clearables clearAllPipelines;
    /** Performs state recovery, if enabled */
    private StateRecovery recovery;
    /** The emergency recovery file found in the current working directory at startup, if any */
    private AtomicReference<EmergencyRecoveryFile> emergencyRecoveryFile = new AtomicReference<>();

    /** A list of threads that execute the chatter protocol. */
    private final List<StoppableThread> chatterThreads = new LinkedList<>();

    /** All components that need to be started or that have dispatch observers. */
    private final PlatformComponents components = new PlatformComponents();

    /** Call this when a reconnect has been completed. */
    private final ReconnectStateLoadedTrigger reconnectStateLoadedDispatcher;

    /** Call this when a state has been loaded from disk. */
    private final DiskStateLoadedTrigger diskStateLoadedDispatcher;

    /**
     * the browser gives the Platform what app to run. There can be multiple Platforms on one
     * computer.
     *
     * @param winNum this is the Nth copy of the Platform running on this machine (N=winNum)
     * @param parameters parameters given to the Platform at the start, for app to use
     * @param crypto an object holding all the public/private key pairs and the CSPRNG state for
     *     this member
     * @param swirldId the ID of the swirld being run
     * @param id the ID number for this member (if this computer has multiple members in one swirld)
     * @param initialAddressBook the address book listing all members in the community
     * @param metrics reference to the metrics system
     * @param mainClassName the name of the app class inheriting from SwirldMain
     * @param swirldName the name of the swirld being run
     * @param appLoader the object used to load the user app
     */
    SwirldsPlatform(
            final int winNum,
            final String[] parameters,
            final Crypto crypto,
            final byte[] swirldId,
            final NodeId id,
            final AddressBook initialAddressBook,
            final Metrics metrics,
            final String mainClassName,
            final String swirldName,
            final SwirldAppLoader appLoader) {

        components.getDispatchBuilder().registerObservers(this);
        components
                .getDispatchBuilder()
                .registerObserver(ShutdownRequestedTrigger.class, Shutdown::shutdown);

        reconnectStateLoadedDispatcher =
                components.getDispatchBuilder().getDispatcher(ReconnectStateLoadedTrigger.class)
                        ::dispatch;
        diskStateLoadedDispatcher =
                components.getDispatchBuilder().getDispatcher(DiskStateLoadedTrigger.class)
                        ::dispatch;

        this.simultaneousSyncThrottle =
                new SimultaneousSyncThrottle(
                        settings.getMaxIncomingSyncsInc() + settings.getMaxOutgoingSyncs());

        // ThreadDumpGenerator.createDeadlock(); // intentionally deadlock, for debugging
        this.mainClassName = mainClassName;
        this.swirldName = swirldName;
        try {
            this.appMain = appLoader.instantiateSwirldMain();
        } catch (final Exception e) {
            CommonUtils.tellUserConsolePopup(
                    "ERROR",
                    "ERROR: There are problems starting class "
                            + mainClassName
                            + "\n"
                            + ExceptionUtils.getStackTrace(e));
            LOG.error(EXCEPTION.getMarker(), "Problems with class {}", mainClassName, e);
        }

        this.winNum = winNum;
        this.parameters = parameters;
        // the memberId of the member running this Platform object
        this.selfId = id;
        // set here, then given to the state in run(). A copy of it is given to hashgraph.
        this.initialAddressBook = initialAddressBook;

        this.eventMapper = new EventMapper(selfId);
        this.chatterEventMapper = new ChatterEventMapper();
        this.hashLogger = new HashLogger(id);

        this.metrics = metrics;

        metrics.getOrCreate(
                StatConstructor.createEnumStat(
                        "PlatformStatus",
                        Metrics.PLATFORM_CATEGORY,
                        PlatformStatus.values(),
                        currentPlatformStatus::get));

        this.intakeCycleStats = new IntakeCycleStats(metrics);

        this.platformMetrics = new PlatformMetrics(this);
        metrics.addUpdater(platformMetrics::update);
        this.consensusMetrics = new ConsensusMetricsImpl(this.selfId, metrics);
        this.addedEventMetrics = new AddedEventMetrics(this.selfId, metrics);
        this.eventIntakeMetrics = new EventIntakeMetrics(metrics);
        this.signedStateMetrics = new SignedStateMetrics(metrics);
        this.syncMetrics = new SyncMetrics(metrics);
        this.networkMetrics = new NetworkMetrics(this);
        metrics.addUpdater(networkMetrics::update);
        this.reconnectMetrics = new ReconnectMetrics(metrics);
        RuntimeMetrics.setup(metrics);

        if (settings.getChatter().isChatterUsed()) {
            chatterCore =
                    new ChatterCore<>(
                            GossipEvent.class,
                            new PrepareChatterEvent(CryptoFactory.getInstance()),
                            settings.getChatter(),
                            networkMetrics::recordPingTime,
                            metrics);
        } else {
            chatterCore = null;
        }

        this.shadowGraph = new ShadowGraph(syncMetrics, initialAddressBook.getSize());

        this.consensusRoundHandler = null;
        this.swirldId = swirldId.clone();
        this.crypto = crypto;

        startUpEventFrozenManager = new StartUpEventFrozenManager(metrics, Instant::now);
        freezeManager = new FreezeManager(this::checkPlatformStatus);

        signedStateFileManager =
                components.add(
                        new SignedStateFileManager(
                                mainClassName, selfId, swirldName, freezeManager::freezeComplete));

        signedStateManager =
                components.add(
                        new SignedStateManager(
                                components.getDispatchBuilder(),
                                getAddressBook(),
                                selfId,
                                ss -> recovery.newSignedStateInRecovery(ss),
                                PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                                signedStateMetrics));

        if (settings.getState().backgroundHashChecking) {
            // This object performs background sanity checks on copies of the state.
            new BackgroundHashChecker(() -> signedStateManager.getLastCompleteSignedState(false));
        }

        systemTransactionHandler =
                new SystemTransactionHandlerImpl(components.getDispatchBuilder());

        consensusRef = new AtomicReference<>();

        reconnectThrottle = new ReconnectThrottle(settings.getReconnect());

        topology =
                new StaticTopology(
                        selfId,
                        initialAddressBook.getSize(),
                        settings.getNumConnections(),
                        !settings.getChatter().isChatterUsed());

        fallenBehindManager =
                new FallenBehindManagerImpl(
                        selfId,
                        topology.getConnectionGraph(),
                        this::checkPlatformStatus,
                        () -> {
                            if (!settings.getChatter().isChatterUsed()) {
                                return;
                            }
                            reconnectController.get().start();
                        },
                        settings.getReconnect());

        components.add(
                new ConsensusHashManager(components.getDispatchBuilder(), initialAddressBook));

        components.add(
                new IssHandler(
                        components.getDispatchBuilder(),
                        Settings.getInstance().getState(),
                        selfId.getId()));

        components.add(new IssMetrics(metrics, initialAddressBook));

        registerNotificationListeners();
    }

    /**
     * Get the transactionMaxBytes in Settings
     *
     * @return integer representing the maximum number of bytes allowed in a transaction
     */
    public static int getTransactionMaxBytes() {
        return Settings.getInstance().getTransactionMaxBytes();
    }

    /** Register all notification callbacks required by the platform. */
    private void registerNotificationListeners() {
        PlatformConstructor.registerFatalListener(signedStateManager, signedStateFileManager);
        PlatformConstructor.registerStateSelfSignedListener(this, selfId);
        PlatformConstructor.registerStateHasEnoughSignaturesListener(
                selfId, freezeManager, signedStateFileManager);
        PlatformConstructor.registerStateLacksSignaturesListener(
                selfId, freezeManager, signedStateMetrics, signedStateFileManager);
        PlatformConstructor.registerNewSignedStateBeingTrackedListener(
                selfId, signedStateFileManager, hashLogger);
    }

    /**
     * Checks if this platform is starting from genesis or a saved state.
     *
     * <p>NOTE: this check will not work (return false) until {@link #loadSavedStateFromDisk()} is
     * called
     *
     * @return true if we are starting from genesis, false if we are starting from a saved state
     */
    private boolean startingFromGenesis() {
        return signedState == null;
    }

    /** {@inheritDoc} */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isMirrorNode() {
        return selfId.isMirror();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isZeroStakeNode() {
        return getAddressBook().getAddress(getSelfId().getId()).isZeroStake();
    }

    /** Get this platform's {@link Cryptography} instance. */
    @Override
    public Cryptography getCryptography() {
        return CryptoFactory.getInstance();
    }

    /**
     * returns a name for this Platform, which includes the app, the swirld, the member id, and the
     * member name. This is useful in the Browser window for showing data about each of the running
     * Platform objects.
     *
     * @return the name for this Platform
     */
    String getPlatformName() {
        // this will be the empty string until the Browser calls setInfoMember. Then it will be
        // correct.
        return platformName;
    }

    /**
     * Store the infoMember that has metadata about this (app,swirld,member) triplet. This also
     * creates the long name that will be returned by getPlatformName.
     *
     * @param infoMember the metadata
     */
    void setInfoMember(final StateHierarchy.InfoMember infoMember) {
        this.infoMember = infoMember;
        this.platformName =
                infoMember.name
                        + " - "
                        + infoMember.swirld.name
                        + " - "
                        + infoMember.swirld.app.name;
    }

    /**
     * Stores a new system transaction that will be added to an event in the future. This is
     * currently called by SignedStateMgr.newSelfSigned() to create the system transaction in which
     * self signs a new signed state.
     *
     * @param systemTransaction the new system transaction to be included in a future Event
     * @return true if successful, false otherwise
     */
    public boolean createSystemTransaction(final SystemTransaction systemTransaction) {
        return createSystemTransaction(systemTransaction, false);
    }

    /**
     * Stores a new system transaction that will be added to an event in the future. This is
     * currently called by SignedStateMgr.newSelfSigned() to create the system transaction in which
     * self signs a new signed state.
     *
     * @param systemTransaction the new system transaction to be included in a future Event
     * @param priority if true, then this transaction will be added to a future event before other
     *     non-priority transactions
     * @return true if successful, false otherwise
     */
    public boolean createSystemTransaction(
            final SystemTransaction systemTransaction, final boolean priority) {
        return swirldStateManager.submitTransaction(systemTransaction, priority);
    }

    /**
     * Load the state from the disk if it is present.
     *
     * @throws SignedStateLoadingException if an exception is encountered while loading the state
     */
    synchronized boolean loadSavedStateFromDisk() throws SignedStateLoadingException {

        final SavedStateInfo[] savedStateFiles =
                getSavedStateFiles(mainClassName, selfId, swirldName);
        if (savedStateFiles == null || savedStateFiles.length == 0) {
            if (settings.isRequireStateLoad()) {
                throw new SignedStateLoadingException("No saved states found on disk!");
            } else {
                return false;
            }
        }

        boolean loadedSavedState = false;

        for (int i = 0; i < savedStateFiles.length; i++) {
            if (i == 0) {
                // load the latest saved state
                try {

                    final DeserializedSignedState deserializedSignedState =
                            readStateFile(savedStateFiles[i].stateFile());

                    final Hash oldHash = deserializedSignedState.originalHash();
                    signedState = deserializedSignedState.signedState();

                    // When loading from disk, we should hash the state every time so that the first
                    // fast copy will
                    // only hash the difference
                    final Hash newHash = rehashTree(signedState.getState());

                    if (settings.isCheckSignedStateFromDisk()) {
                        if (newHash.equals(oldHash)) {
                            LOG.info(
                                    STARTUP.getMarker(),
                                    "Signed state loaded from disk has a valid hash.");
                        } else {
                            LOG.error(
                                    STARTUP.getMarker(),
                                    "ERROR: Signed state loaded from disk has an invalid hash!\n"
                                            + "disk:{}\n"
                                            + "calc:{}",
                                    oldHash,
                                    newHash);
                        }
                    }

                    LOG.info(
                            STARTUP.getMarker(),
                            "Information for state loaded from disk:\n{}\n{}",
                            () -> signedState.getState().getPlatformState().getInfoString(),
                            () ->
                                    generateHashDebugString(
                                            signedState.getState(),
                                            StateSettings.getDebugHashDepth()));
                    LOG.info(
                            LogMarker.STATE_HASH.getMarker(),
                            "{}RESTART: Signed state loaded from disk.",
                            HASH_STREAM_OPERATION_PREFIX);

                    previousSoftwareVersion =
                            signedState
                                    .getState()
                                    .getPlatformState()
                                    .getPlatformData()
                                    .getCreationSoftwareVersion();
                    initialState = signedState.getState().copy();
                    initialState
                            .getPlatformState()
                            .getPlatformData()
                            .setCreationSoftwareVersion(appMain.getSoftwareVersion());

                    initialState
                            .getSwirldState()
                            .init(
                                    this,
                                    initialAddressBook.copy(),
                                    initialState.getSwirldDualState(),
                                    InitTrigger.RESTART,
                                    previousSoftwareVersion);

                    // Intentionally reserve & release state. If the signed state manager rejects
                    // this signed state, we
                    // want the release of an explicit reference to cause the state to be cleaned
                    // up.
                    signedState.reserveState();
                    signedStateManager.addCompleteSignedState(
                            signedState, SourceOfSignedState.DISK);
                    signedState.releaseState();

                    loadedSavedState = true;
                } catch (final Exception e) {
                    signedState = null;
                    throw new SignedStateLoadingException(
                            "Exception while reading signed state!", e);
                }
            } else {
                // delete the older ones
                try {
                    deleteDirectoryAndLog(savedStateFiles[i].getDir());
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return loadedSavedState;
    }

    /**
     * Loads the signed state data into consensus and event mapper
     *
     * @param signedState the state to get the data from
     */
    void loadIntoConsensusAndEventMapper(final SignedState signedState) {
        consensusRef.set(
                new ConsensusImpl(
                        consensusMetrics,
                        consensusRoundHandler::addMinGenInfo,
                        getAddressBook(),
                        signedState));

        shadowGraph.initFromEvents(
                EventUtils.prepareForShadowGraph(
                        // we need to pass in a copy of the array, otherwise prepareForShadowGraph
                        // will rearrange the
                        // events in the signed state which will cause issues for other components
                        // that depend on it
                        signedState.getEvents().clone()),
                // we need to provide the minGen from consensus so that expiry matches after a
                // restart/reconnect
                consensusRef.get().getMinRoundGeneration());

        // Data that is needed for the intake system to work
        for (final EventImpl e : signedState.getEvents()) {
            eventMapper.eventAdded(e);
        }

        if (settings.getChatter().isChatterUsed()) {
            chatterEventMapper.loadFromSignedState(signedState);
            chatterCore.loadFromSignedState(signedState);
        }

        transactionTracker.reset();

        LOG.info(
                STARTUP.getMarker(),
                "Last known events after restart are {}",
                eventMapper.getMostRecentEventsByEachCreator());
    }

    /**
     * Used to load the state received from the sender.
     *
     * @param signedState the signed state that was received from the sender
     */
    void loadReconnectState(final SignedState signedState) {
        // the state was received, so now we load its data into different objects
        LOG.info(
                LogMarker.STATE_HASH.getMarker(),
                "{}RECONNECT: loadReconnectState: reloading state",
                HASH_STREAM_OPERATION_PREFIX);
        LOG.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
        try {

            reconnectStateLoadedDispatcher.dispatch(
                    signedState.getRound(), signedState.getState().getHash());

            // Make sure the signature set in the signed state is big enough for the current address
            // book
            signedState.expandSigSetIfNeeded(initialAddressBook);

            // It's important to call init() before loading the signed state. The loading process
            // makes copies
            // of the state, and we want to be sure that the first state in the chain of copies has
            // been initialized.
            signedState
                    .getSwirldState()
                    .init(
                            this,
                            initialAddressBook.copy(),
                            signedState.getState().getSwirldDualState(),
                            InitTrigger.RECONNECT,
                            signedState
                                    .getState()
                                    .getPlatformState()
                                    .getPlatformData()
                                    .getCreationSoftwareVersion());

            swirldStateManager.loadFromSignedState(signedState);

            // Intentionally reserve & release state. If the signed state manager rejects this
            // signed state, we
            // want the release of an explicit reference to cause the state to be cleaned up.
            signedState.reserveState();
            getSignedStateManager()
                    .addCompleteSignedState(signedState, SourceOfSignedState.RECONNECT);
            signedState.releaseState();

            loadIntoConsensusAndEventMapper(signedState);
            // eventLinker is not thread safe, which is not a problem regularly because it is only
            // used by a single
            // thread. after a reconnect, it needs to load the minimum generation from a state on a
            // different thread,
            // so the intake thread is paused before the data is loaded and unpaused after. this
            // ensures that the
            // thread will get the up-to-date data loaded
            new PauseAndLoad(getIntakeQueue(), eventLinker).loadFromSignedState(signedState);

            getConsensusHandler().loadDataFromSignedState(signedState, true);

            // Notify any listeners that the reconnect has been completed
            try {
                signedState.reserveState();
                NotificationFactory.getEngine()
                        .dispatch(
                                ReconnectCompleteListener.class,
                                new ReconnectCompleteNotification(
                                        signedState.getRound(),
                                        signedState.getConsensusTimestamp(),
                                        signedState.getState().getSwirldState()));
            } finally {
                signedState.releaseState();
            }
        } catch (final RuntimeException e) {
            LOG.debug(
                    RECONNECT.getMarker(),
                    "`loadReconnectState` : FAILED, reason: {}",
                    e.getMessage());
            // if the loading fails for whatever reason, we clear all data again in case some of it
            // has been loaded
            clearAllPipelines.clear();
            throw e;
        }

        LOG.debug(
                RECONNECT.getMarker(),
                "`loadReconnectState` : reconnect complete notifications finished. Resetting"
                        + " fallen-behind");
        getSyncManager().resetFallenBehind();
        LOG.debug(
                RECONNECT.getMarker(),
                "`loadReconnectState` : resetting fallen-behind & reloading state, finished,"
                        + " succeeded`");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStateRecoveryInProgress() {
        return stateRecoveryInProgress;
    }

    /** Recover a signed state from event stream files */
    private void stateRecover() {
        recovery =
                new StateRecovery(
                        selfId,
                        StaticSettingsProvider.getSingleton(),
                        getAddress().getMemo(),
                        signedState,
                        eventStreamManager,
                        signedStateFileManager,
                        consensusRoundHandler);

        stateRecoveryInProgress = true;
        recovery.execute();

        // update dual state status to clear any possible inconsistency between freezeTime and
        // lastFrozenTime
        swirldStateManager.clearFreezeTimes();
    }

    /**
     * First part of initialization. This was split up so that appMain.init() could be called before
     * {@link StateLoadedFromDiskNotification} would be dispatched. Eventually, this should be split
     * into more discrete parts.
     */
    synchronized void initializeFirstStep() {

        // if this setting is 0 or less, there is no startup freeze
        if (settings.getFreezeSecondsAfterStartup() > 0) {
            final Instant startUpEventFrozenEndTime =
                    Instant.now().plusSeconds(settings.getFreezeSecondsAfterStartup());
            startUpEventFrozenManager.setStartUpEventFrozenEndTime(startUpEventFrozenEndTime);
            LOG.info(
                    STARTUP.getMarker(),
                    "startUpEventFrozenEndTime: {}",
                    () -> startUpEventFrozenEndTime);
        }

        // initializes EventStreamManager instance
        final Address address = this.getAddress();
        if (address.getMemo() != null && !address.getMemo().isEmpty()) {
            initEventStreamManager(address.getMemo());
        } else {
            initEventStreamManager(String.valueOf(selfId));
        }

        buildEventHandlers();

        transactionSubmitter =
                new TransactionSubmitter(
                        currentPlatformStatus::get,
                        PlatformConstructor.settingsProvider(),
                        isZeroStakeNode(),
                        swirldStateManager::submitTransaction,
                        new TransactionMetrics(metrics));

        if (settings.isWaitAtStartup()) {
            this.startupThrottle =
                    new StartupThrottle(
                            initialAddressBook,
                            selfId,
                            this::checkPlatformStatus,
                            settings.isEnableBetaMirror());
        } else {
            this.startupThrottle = StartupThrottle.getNoOpInstance();
        }

        this.transactionTracker = new TransactionTracker();

        if (signedState != null) {
            loadIntoConsensusAndEventMapper(signedState);
        } else {
            consensusRef.set(
                    new ConsensusImpl(
                            consensusMetrics,
                            consensusRoundHandler::addMinGenInfo,
                            getAddressBook()));
        }

        criticalQuorum = new CriticalQuorumImpl(initialAddressBook);

        // build the event intake classes
        buildEventIntake();
        if (signedState != null) {
            eventLinker.loadFromSignedState(signedState);
        }

        try {
            appMain.init(this, selfId);
        } catch (final Exception e) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "Exception while calling {}.init! Exiting...",
                    mainClassName,
                    e);
            com.swirlds.platform.system.SystemUtils.exitSystem(SWIRLD_MAIN_THREW_EXCEPTION);
        }

        clearAllPipelines =
                Clearables.of(
                        getIntakeQueue(),
                        getEventMapper(),
                        getShadowGraph(),
                        preConsensusEventHandler,
                        consensusRoundHandler,
                        swirldStateManager);
    }

    /**
     * This observer is called when the most recent signed state be dumped to disk.
     *
     * @param reason reason why the state is being dumped, e.g. "fatal" or "iss". Is used as a part
     *     of the file path for the dumped state files, so this string should not contain any
     *     special characters or whitespace.
     * @param blocking if this method should block until the operation has been completed
     */
    @Observer(dispatchType = StateDumpRequestedTrigger.class)
    public void stateDumpRequestedObserver(final String reason, final Boolean blocking) {
        try (final AutoCloseableWrapper<SignedState> wrapper =
                signedStateManager.getLastSignedState()) {
            signedStateFileManager.dumpState(wrapper.get(), reason, blocking);
        }
    }

    /**
     * This observer is called when a system freeze is requested. Permanently stops event creation
     * and gossip.
     *
     * @param reason the reason why the system is being frozen.
     */
    @Observer(dispatchType = HaltRequestedTrigger.class)
    public void haltRequestedObserver(final String reason) {
        LOG.error(EXCEPTION.getMarker(), "System halt requested. Reason: {}", reason);
        freezeManager.freezeEventCreation();
        for (final StoppableThread thread : chatterThreads) {
            thread.stop();
        }
    }

    /**
     * Creates and wires up all the classes responsible for accepting events from gossip, creating
     * new events, and routing those events throughout the system.
     */
    private void buildEventIntake() {
        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(
                        new ShadowGraphEventObserver(shadowGraph),
                        consensusRoundHandler,
                        preConsensusEventHandler,
                        eventMapper,
                        addedEventMetrics,
                        startupThrottle,
                        transactionTracker,
                        criticalQuorum,
                        eventIntakeMetrics);
        if (settings.getChatter().isChatterUsed()) {
            dispatcher.addObserver(new ChatterNotifier(selfId, chatterCore));
            dispatcher.addObserver(chatterEventMapper);
        }

        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);

        final List<Predicate<ChatterEventDescriptor>> isDuplicateChecks = new ArrayList<>();
        isDuplicateChecks.add(d -> shadowGraph.isHashInGraph(d.getHash()));
        if (settings.getChatter().isChatterUsed()) {
            final OrphanBufferingLinker orphanBuffer =
                    new OrphanBufferingLinker(
                            parentFinder, settings.getChatter().getFutureGenerationLimit());
            metrics.getOrCreate(
                    new FunctionGauge.Config<>(
                                    "intake",
                                    "numOrphans",
                                    Integer.class,
                                    orphanBuffer::getNumOrphans)
                            .withDescription("the number of events without parents buffered")
                            .withFormat("%d"));
            eventLinker = orphanBuffer;
            // when using chatter an event could be an orphan, in this case it will be stored in the
            // orphan set
            // when its parents are found, or become ancient, it will move to the shadowgraph
            // non-orphans are also stored in the shadowgraph
            // to dedupe, we need to check both
            isDuplicateChecks.add(orphanBuffer::isOrphan);
        } else {
            eventLinker = new InOrderLinker(parentFinder, eventMapper::getMostRecentEvent);
        }

        final EventIntake eventIntake =
                new EventIntake(
                        selfId,
                        eventLinker,
                        consensusRef::get,
                        initialAddressBook,
                        dispatcher,
                        intakeCycleStats,
                        shadowGraph);

        final EventCreator eventCreator;
        if (settings.getChatter().isChatterUsed()) {
            // chatter has a separate event creator in a different thread. having 2 event creators
            // creates the risk
            // of forking, so a NPE is preferable to a fork
            eventCreator = null;
        } else {
            eventCreator =
                    new EventCreator(
                            this,
                            selfId,
                            PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                            consensusRef::get,
                            swirldStateManager.getTransactionPool(),
                            eventIntake::addEvent,
                            eventMapper,
                            eventMapper,
                            transactionTracker,
                            swirldStateManager.getTransactionPool(),
                            freezeManager::isFreezeStarted,
                            new EventCreationRules(List.of(startupThrottle)));
        }

        final List<GossipEventValidator> validators = new ArrayList<>();
        // it is very important to discard ancient events, otherwise the deduplication will not
        // work, since it doesn't
        // track ancient events
        validators.add(new AncientValidator(consensusRef::get));
        validators.add(new EventDeduplication(isDuplicateChecks, eventIntakeMetrics));
        validators.add(StaticValidators::isParentDataValid);
        if (settings.isEnableBetaMirror()) {
            validators.add(new ZeroStakeValidator(initialAddressBook));
        }
        validators.add(new TransactionSizeValidator(settings.getMaxTransactionBytesPerEvent()));
        if (settings.isVerifyEventSigs()) {
            validators.add(new SignatureValidator(initialAddressBook));
        }
        final GossipEventValidators eventValidators = new GossipEventValidators(validators);

        /* validates events received from gossip */
        final EventValidator eventValidator =
                new EventValidator(eventValidators, eventIntake::addUnlinkedEvent);

        final EventTaskDispatcher taskDispatcher =
                new EventTaskDispatcher(
                        eventValidator,
                        eventCreator,
                        eventIntake::addUnlinkedEvent,
                        eventIntakeMetrics,
                        intakeCycleStats);

        final InterruptableConsumer<EventIntakeTask> intakeHandler;
        if (settings.getChatter().isChatterUsed()) {
            intakeCycle = new SequenceCycle<>(taskDispatcher::dispatchTask);
            intakeHandler = intakeCycle;
        } else {
            intakeHandler = taskDispatcher::dispatchTask;
        }
        intakeQueue =
                components.add(
                        new QueueThreadConfiguration<EventIntakeTask>()
                                .setNodeId(selfId.getId())
                                .setComponent(PLATFORM_THREAD_POOL_NAME)
                                .setThreadName("event-intake")
                                .setHandler(intakeHandler)
                                .setCapacity(settings.getEventIntakeQueueSize())
                                .build());
    }

    /** Build all the classes required for events and transactions to flow through the system */
    private void buildEventHandlers() {

        // Queue thread that stores and handles signed states that need to be hashed and have
        // signatures collected.
        final QueueThread<SignedState> stateHashSignQueueThread =
                PlatformConstructor.stateHashSignQueue(selfId.getId(), signedStateManager);
        stateHashSignQueueThread.start();

        if (signedState != null) {
            LOG.debug(
                    STARTUP.getMarker(),
                    () ->
                            new SavedStateLoadedPayload(
                                            signedState.getRound(),
                                            signedState.getConsensusTimestamp(),
                                            startUpEventFrozenManager
                                                    .getStartUpEventFrozenEndTime())
                                    .toString());

            buildEventHandlersFromState(initialState, stateHashSignQueueThread);

            consensusRoundHandler.loadDataFromSignedState(signedState, false);
        } else {
            final State state = newState();
            buildEventHandlersFromState(state, stateHashSignQueueThread);

            // if we are not starting from a saved state, don't freeze on startup
            startUpEventFrozenManager.setStartUpEventFrozenEndTime(null);
        }
    }

    private void buildEventHandlersFromState(
            final State state, final QueueThread<SignedState> stateHashSignQueueThread) {

        swirldStateManager =
                PlatformConstructor.swirldStateManager(
                        selfId,
                        systemTransactionHandler,
                        metrics,
                        PlatformConstructor.settingsProvider(),
                        this::estimateTime,
                        freezeManager::isFreezeStarted,
                        state);

        // SwirldStateManager will get a copy of the state loaded, that copy will become stateCons.
        // The original state will be saved in the SignedStateMgr and will be deleted when it
        // becomes old
        preConsensusEventHandler =
                components.add(
                        PlatformConstructor.preConsensusEventHandler(
                                selfId, swirldStateManager, consensusMetrics));
        consensusRoundHandler =
                components.add(
                        PlatformConstructor.consensusHandler(
                                components.getDispatchBuilder(),
                                selfId.getId(),
                                PlatformConstructor.settingsProvider(),
                                swirldStateManager,
                                new ConsensusHandlingMetrics(metrics),
                                eventStreamManager,
                                initialAddressBook,
                                stateHashSignQueueThread,
                                freezeManager::freezeStarted,
                                appMain.getSoftwareVersion()));
    }

    private State newState() {
        final State state = new State();
        state.setSwirldState(appMain.newState());
        state.setDualState(new DualStateImpl());

        // if genesisFreezeTime is positive, and the nodes start from genesis
        if (genesisFreezeTime > 0) {
            state.getPlatformDualState().setFreezeTime(Instant.ofEpochSecond(genesisFreezeTime));
        }
        // hashgraph and state get separate copies of
        state.getSwirldState()
                .init(
                        this,
                        initialAddressBook.copy(),
                        state.getSwirldDualState(),
                        InitTrigger.GENESIS,
                        SoftwareVersion.NO_VERSION);

        return state;
    }

    /**
     * Start the platform, which will in turn start the app and all the syncing threads. When using
     * the normal browser, only one Platform is running. But with config.txt, multiple can be
     * running.
     */
    void run() {
        syncManager =
                components.add(
                        new SyncManagerImpl(
                                intakeQueue,
                                topology.getConnectionGraph(),
                                selfId,
                                new EventCreationRules(
                                        List.of(
                                                selfId,
                                                swirldStateManager.getTransactionPool(),
                                                startUpEventFrozenManager,
                                                freezeManager)),
                                List.of(freezeManager, startUpEventFrozenManager),
                                new TransThrottleSyncAndCreateRules(
                                        List.of(
                                                swirldStateManager.getTransactionPool(),
                                                swirldStateManager,
                                                startupThrottle)),
                                signedStateFileManager::getLastRoundSavedToDisk,
                                signedStateManager::getLastCompleteRound,
                                transactionTracker,
                                criticalQuorum,
                                initialAddressBook,
                                fallenBehindManager));

        components.start();

        if (signedState != null) {
            // If we loaded from disk then call the appropriate dispatch. This dispatch
            // must wait until after components have been started.
            diskStateLoadedDispatcher.dispatch(
                    signedState.getRound(), signedState.getState().getHash());
        }

        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            // If there is no default uncaught exception handler already provided, make sure we set
            // one to avoid threads
            // silently dying from exceptions.
            Thread.setDefaultUncaughtExceptionHandler(
                    (Thread t, Throwable e) ->
                            LOG.error(
                                    EXCEPTION.getMarker(),
                                    "exception on thread {}",
                                    t.getName(),
                                    e));
        }

        this.eventTaskCreator =
                new EventTaskCreator(
                        eventMapper,
                        // hashgraph and state get separate copies of the address book
                        initialAddressBook.copy(),
                        selfId,
                        eventIntakeMetrics,
                        intakeQueue,
                        StaticSettingsProvider.getSingleton(),
                        syncManager,
                        ThreadLocalRandom::current);

        // a genesis event could be created here, but it isn't needed. This member will naturally
        // create an
        // event after their first sync, where the first sync will involve sending no events.

        final Thread mainThread =
                new ThreadConfiguration()
                        .setPriority(settings.getThreadPriorityNonSync())
                        .setNodeId(selfId.getId())
                        .setComponent(PLATFORM_THREAD_POOL_NAME)
                        .setThreadName("appMainRun")
                        .setRunnable(appMain)
                        .build();

        shadowgraphSynchronizer =
                new ShadowGraphSynchronizer(
                        getShadowGraph(),
                        getNumMembers(),
                        syncMetrics,
                        consensusRef::get,
                        eventTaskCreator::syncDone,
                        eventTaskCreator::addEvent,
                        syncManager,
                        PlatformConstructor.parallelExecutor(),
                        PlatformConstructor.settingsProvider(),
                        true,
                        () -> {});

        final Runnable stopGossip =
                settings.getChatter().isChatterUsed()
                        ? chatterCore::stopChatter
                        // wait and acquire all sync ongoing locks and release them immediately
                        // this will ensure any ongoing sync are finished before we start reconnect
                        // no new sync will start because we have a fallen behind status
                        : getSimultaneousSyncThrottle()::waitForAllSyncsToFinish;
        reconnectHelper =
                new ReconnectHelper(
                        stopGossip,
                        clearAllPipelines,
                        getSwirldStateManager()::getConsensusState,
                        getSignedStateManager()::getLastCompleteRound,
                        new ReconnectLearnerThrottle(selfId, settings.getReconnect()),
                        this::loadReconnectState,
                        new ReconnectLearnerFactory(
                                initialAddressBook, settings.getReconnect(), reconnectMetrics));
        if (settings.getChatter().isChatterUsed()) {
            reconnectController.set(
                    new ReconnectController(reconnectHelper, chatterCore::startChatter));
        }

        // In recover mode, skip sync with other nodes,
        // and don't start main to accept any new transactions
        if (!settings.isEnableStateRecovery()) {
            mainThread.start();
            if (settings.getChatter().isChatterUsed()) {
                startChatterNetwork();
            } else {
                startSyncNetwork();
            }
        }

        final ScheduledExecutorService metricsWriterExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r ->
                                new ThreadConfiguration()
                                        .setPriority(SettingsCommon.threadPriorityNonSync)
                                        .setNodeId(selfId.getId())
                                        .setComponent(PLATFORM_THREAD_POOL_NAME)
                                        .setThreadName("MetricsWriter")
                                        .setRunnable(r)
                                        .build());
        final MetricsWriterService writerService =
                new MetricsWriterService(metrics, selfId, metricsWriterExecutor);
        writerService.start();

        if (settings.isRunPauseCheckTimer()) {
            // periodically check current time stamp to detect whether the java application
            // has been paused for a long period
            final Timer pauseCheckTimer = new Timer("pause check", true);
            pauseCheckTimer.schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            final long currentTimeStamp = System.currentTimeMillis();
                            if ((currentTimeStamp - pauseCheckTimeStamp) > PAUSE_ALERT_INTERVAL
                                    && pauseCheckTimeStamp != 0) {
                                LOG.error(
                                        EXCEPTION.getMarker(),
                                        "ERROR, a pause larger than {} is detected ",
                                        PAUSE_ALERT_INTERVAL);
                            }
                            pauseCheckTimeStamp = currentTimeStamp;
                        }
                    },
                    0,
                    PAUSE_ALERT_INTERVAL / 2);
        }

        if (settings.isEnableStateRecovery()) {
            stateRecover();
        }

        synchronized (this) {
            if (signedState != null) {
                signedState = null; // we won't need this after this point
                initialState = null; // we won't need this after this point
            }
        }

        // When the SwirldMain quits, end the run() for this platform instance
        try {
            mainThread.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Construct and start all networking components that are common to both types of networks, sync
     * and chatter. This is everything related to creating and managing connections to
     * neighbors.Only the connection managers are returned since this should be the only point of
     * entry for other components.
     *
     * @return an instance that maintains connection managers for all connections to neighbors
     */
    public StaticConnectionManagers startCommonNetwork() {
        final SocketFactory socketFactory =
                PlatformConstructor.socketFactory(crypto.getKeysAndCerts());
        // create an instance that can create new outbound connections
        final OutboundConnectionCreator connectionCreator =
                new OutboundConnectionCreator(
                        selfId,
                        StaticSettingsProvider.getSingleton(),
                        this,
                        socketFactory,
                        initialAddressBook);
        final StaticConnectionManagers connectionManagers =
                new StaticConnectionManagers(topology, connectionCreator);
        final InboundConnectionHandler inboundConnectionHandler =
                new InboundConnectionHandler(
                        this,
                        selfId,
                        initialAddressBook,
                        connectionManagers::newConnection,
                        StaticSettingsProvider.getSingleton());
        // allow other members to create connections to me
        final Address address = getAddressBook().getAddress(selfId.getId());
        final ConnectionServer connectionServer =
                new ConnectionServer(
                        address.getListenAddressIpv4(),
                        address.getListenPortIpv4(),
                        socketFactory,
                        inboundConnectionHandler::handle);
        new StoppableThreadConfiguration<ConnectionServer>()
                .setPriority(settings.getThreadPrioritySync())
                .setNodeId(selfId.getId())
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("connectionServer")
                .setWork(connectionServer)
                .build()
                .start();
        return connectionManagers;
    }

    /**
     * Constructs and starts all networking components needed for a chatter network to run: readers,
     * writers and a separate event creation thread.
     */
    public void startChatterNetwork() {
        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        // first create all instances because of thread safety
        for (final NodeId otherId : topology.getNeighbors()) {
            chatterCore.newPeerInstance(otherId.getId(), eventTaskCreator::addEvent);
        }
        final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor("chatter");
        for (final NodeId otherId : topology.getNeighbors()) {
            final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId.getId());
            final ShadowGraphSynchronizer chatterSynchronizer =
                    new ShadowGraphSynchronizer(
                            getShadowGraph(),
                            getNumMembers(),
                            syncMetrics,
                            consensusRef::get,
                            sr -> {},
                            eventTaskCreator::addEvent,
                            syncManager,
                            PlatformConstructor.parallelExecutor(),
                            PlatformConstructor.settingsProvider(),
                            false,
                            () -> {
                                // start accepting events into the chatter queue
                                chatterPeer.communicationState().chatterSyncStartingPhase3();
                                // wait for any intake event currently being processed to finish
                                intakeCycle.waitForCurrentSequenceEnd();
                            });
            chatterThreads.add(
                    new StoppableThreadConfiguration<>()
                            .setPriority(Thread.NORM_PRIORITY)
                            .setNodeId(selfId.getId())
                            .setComponent(PLATFORM_THREAD_POOL_NAME)
                            .setOtherNodeId(otherId.getId())
                            .setThreadName("ChatterReader")
                            .setWork(
                                    new NegotiatorThread(
                                            connectionManagers.getManager(
                                                    otherId, topology.shouldConnectTo(otherId)),
                                            List.of(
                                                    new VersionCompareHandshake(
                                                            appMain.getSoftwareVersion(),
                                                            !settings
                                                                    .isGossipWithDifferentVersions()),
                                                    new VersionCompareHandshake(
                                                            PlatformVersion.locateOrDefault(),
                                                            !settings
                                                                    .isGossipWithDifferentVersions())),
                                            new NegotiationProtocols(
                                                    List.of(
                                                            new EmergencyReconnectProtocol(
                                                                    otherId,
                                                                    emergencyRecoveryFile,
                                                                    reconnectThrottle,
                                                                    signedStateManager,
                                                                    settings.getReconnect()
                                                                            .getAsyncStreamTimeoutMilliseconds(),
                                                                    reconnectMetrics,
                                                                    reconnectController.get(),
                                                                    getCrypto()),
                                                            new ReconnectProtocol(
                                                                    otherId,
                                                                    reconnectThrottle,
                                                                    () ->
                                                                            signedStateManager
                                                                                    .getLastCompleteSignedState(
                                                                                            false)
                                                                                    .get(),
                                                                    settings.getReconnect()
                                                                            .getAsyncStreamTimeoutMilliseconds(),
                                                                    reconnectMetrics,
                                                                    reconnectController.get(),
                                                                    new DefaultSignedStateValidator(
                                                                            getCrypto())),
                                                            new ChatterSyncProtocol(
                                                                    chatterPeer
                                                                            .communicationState(),
                                                                    chatterPeer.outputAggregator(),
                                                                    chatterSynchronizer),
                                                            new ChatterProtocol(
                                                                    chatterPeer,
                                                                    parallelExecutor)))))
                            .build(true));
        }
        final OtherParentTracker otherParentTracker = new OtherParentTracker();
        final EventCreationRules eventCreationRules =
                LoggingEventCreationRules.create(
                        List.of(
                                startUpEventFrozenManager,
                                freezeManager,
                                fallenBehindManager,
                                new ChatteringRule(
                                        settings.getChatter().getChatteringCreationThreshold(),
                                        chatterCore.getPeerInstances().stream()
                                                .map(PeerInstance::communicationState)
                                                .toList()),
                                swirldStateManager.getTransactionPool(),
                                new BelowIntCreationRule(
                                        intakeQueue::size,
                                        settings.getChatter().getChatterIntakeThrottle())),
                        List.of(
                                StaticCreationRules::nullOtherParent,
                                otherParentTracker,
                                new AncientParentsRule(consensusRef::get),
                                criticalQuorum));
        final ChatterEventCreator chatterEventCreator =
                new ChatterEventCreator(
                        this,
                        selfId,
                        PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
                        swirldStateManager.getTransactionPool(),
                        CommonUtils.combineConsumers(
                                eventTaskCreator::createdEvent,
                                otherParentTracker::track,
                                chatterEventMapper::mapEvent),
                        chatterEventMapper::getMostRecentEvent,
                        eventCreationRules);

        if (startingFromGenesis()) {
            // if we are starting from genesis, we will create a genesis event, which is the only
            // event that will
            // ever be created without an other-parent
            chatterEventCreator.createGenesisEvent();
        }
        final EventCreatorThread eventCreatorThread =
                new EventCreatorThread(
                        selfId,
                        settings.getChatter().getAttemptedChatterEventPerSecond(),
                        initialAddressBook,
                        chatterEventCreator::createEvent);

        clearAllPipelines =
                Clearables.of(
                        // chatter event creator needs to be cleared first, because it sends event
                        // to intake
                        eventCreatorThread,
                        getIntakeQueue(),
                        // eventLinker is not thread safe, so the intake thread needs to be paused
                        // while its being cleared
                        new PauseAndClear(getIntakeQueue(), eventLinker),
                        eventMapper,
                        chatterEventMapper,
                        getShadowGraph(),
                        preConsensusEventHandler,
                        consensusRoundHandler,
                        swirldStateManager);
        eventCreatorThread.start();
    }

    /**
     * Constructs and starts all networking components needed for a sync network to run: heartbeats,
     * callers, listeners
     */
    public void startSyncNetwork() {
        final StaticConnectionManagers connectionManagers = startCommonNetwork();

        sharedConnectionLocks = new SharedConnectionLocks(topology, connectionManagers);
        final MultiProtocolResponder protocolHandlers =
                new MultiProtocolResponder(
                        List.of(
                                ProtocolMapping.map(
                                        UnidirectionalProtocols.SYNC.getInitialByte(),
                                        new SyncProtocolResponder(
                                                simultaneousSyncThrottle,
                                                shadowgraphSynchronizer,
                                                syncManager,
                                                syncManager::shouldAcceptSync,
                                                syncMetrics)),
                                ProtocolMapping.map(
                                        UnidirectionalProtocols.RECONNECT.getInitialByte(),
                                        new ReconnectProtocolResponder(
                                                signedStateManager,
                                                settings.getReconnect(),
                                                reconnectThrottle,
                                                reconnectMetrics)),
                                ProtocolMapping.map(
                                        UnidirectionalProtocols.HEARTBEAT.getInitialByte(),
                                        HeartbeatProtocolResponder::heartbeatProtocol)));

        for (final NodeId otherId : topology.getNeighbors()) {
            // create and start new threads to listen for incoming sync requests
            new StoppableThreadConfiguration<Listener>()
                    .setPriority(Thread.NORM_PRIORITY)
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setOtherNodeId(otherId.getId())
                    .setThreadName("listener")
                    .setWork(
                            new Listener(
                                    protocolHandlers,
                                    connectionManagers.getManager(otherId, false)))
                    .build()
                    .start();

            // create and start new thread to send heartbeats on the SyncCaller channels
            new StoppableThreadConfiguration<HeartbeatSender>()
                    .setPriority(settings.getThreadPrioritySync())
                    .setNodeId(selfId.getId())
                    .setComponent(PLATFORM_THREAD_POOL_NAME)
                    .setThreadName("heartbeat")
                    .setOtherNodeId(otherId.getId())
                    .setWork(
                            new HeartbeatSender(
                                    otherId,
                                    sharedConnectionLocks,
                                    networkMetrics,
                                    PlatformConstructor.settingsProvider()))
                    .build()
                    .start();
        }

        // start the timing AFTER the initial pause
        metrics.resetAll();
        // create and start threads to call other members
        for (int i = 0; i < settings.getMaxOutgoingSyncs(); i++) {
            spawnSyncCaller(i);
        }
    }

    /** Spawn a thread to initiate syncs with other users */
    private void spawnSyncCaller(final int callerNumber) {
        // create a caller that will run repeatedly to call random members other than selfId
        final SyncCaller syncCaller =
                new SyncCaller(
                        this,
                        getAddressBook(),
                        selfId,
                        callerNumber,
                        reconnectHelper,
                        new DefaultSignedStateValidator(getCrypto()),
                        platformMetrics);

        /* the thread that repeatedly initiates syncs with other members */
        final Thread syncCallerThread =
                new ThreadConfiguration()
                        .setPriority(settings.getThreadPrioritySync())
                        .setNodeId(selfId.getId())
                        .setComponent(PLATFORM_THREAD_POOL_NAME)
                        .setThreadName("syncCaller-" + callerNumber)
                        .setRunnable(syncCaller)
                        .build();

        syncCallerThread.start();
    }

    /**
     * return the rectangle of the recommended window size and location for this instance of the
     * Platform. Both consoles and windows are created to fit in this rectangle, by default.
     *
     * @return the recommended Rectangle for this Platform's window
     */
    private Rectangle winRect() {
        // the goal is to arrange windows on the screen so that the leftmost and rightmost windows
        // just
        // touch the edge of the screen with their outermost border. But the rest of the windows
        // overlap
        // with them and with each other such that all of the border of one window (ecept 2 pixels)
        // overlaps
        // the content of its neighbors. This should look fine on any OS where the borders are thin
        // (i.e.,
        // all except Windows 10), and should also look good on Windows 10, by making the invisible
        // borders
        // overlap the adjacent window rather than looking like visible gaps.
        // In addition, extra space is added to either the left or right side of the
        // screen, whichever is likely to have the close button for the Browser window that lies
        // behind the
        // Platform windows.

        final int leftGap = (SystemUtils.IS_OS_WINDOWS ? 0 : 25); // extra space at left screen edge
        final int rightGap =
                (SystemUtils.IS_OS_WINDOWS ? 50 : 0); // extra space at right screen edge
        final Rectangle screenSize =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        final int winCount = getOwnHostCount(getAddressBook());
        final int contentWidth =
                (screenSize.width - leftGap - rightGap - Browser.insets.left - Browser.insets.right)
                        / winCount;
        final int x = screenSize.x + leftGap + contentWidth * this.winNum;
        final int y = screenSize.y;
        final int width = contentWidth + Browser.insets.left + Browser.insets.right;
        final int height = screenSize.height;
        return new Rectangle(x, y, width, height);
    }

    /** {@inheritDoc} */
    @Override
    public void preEvent() {
        if (!settings
                .isEnableStateRecovery()) { // recover mode don't allow any new transaction being
            // submitted
            // give the app one last chance to create a non-system transaction and give the platform
            // one last chance to create a system transaction
            appMain.preEvent();
        }
    }

    /** {@inheritDoc} */
    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    /**
     * @return the SyncManager used by this platform
     */
    SyncManagerImpl getSyncManager() {
        return syncManager;
    }

    /**
     * Get the shadow graph used by this platform
     *
     * @return the {@link ShadowGraph} used by this platform
     */
    ShadowGraph getShadowGraph() {
        return shadowGraph;
    }

    /**
     * @return the signed state manager for this platform
     */
    SignedStateManager getSignedStateManager() {
        return signedStateManager;
    }

    /**
     * @return locks used to synchronize usage of outbound connections
     */
    SharedConnectionLocks getSharedConnectionLocks() {
        return sharedConnectionLocks;
    }

    /**
     * @return the crypto object for this platform
     */
    Crypto getCrypto() {
        return crypto;
    }

    /**
     * @return the instance responsible for creating event tasks
     */
    public EventTaskCreator getEventTaskCreator() {
        return eventTaskCreator;
    }

    /** Get the {@link EventMapper} for this platform. */
    public EventMapper getEventMapper() {
        return eventMapper;
    }

    /** Get the event intake queue for this platform. */
    public QueueThread<EventIntakeTask> getIntakeQueue() {
        return intakeQueue;
    }

    /**
     * @return the consensus object used by this platform
     */
    public Consensus getConsensus() {
        return consensusRef.get();
    }

    /**
     * @return the object that tracks recent events created
     */
    public CriticalQuorum getCriticalQuorum() {
        return criticalQuorum;
    }

    /**
     * @return the object that tracks user transactions in the hashgraph
     */
    TransactionTracker getTransactionTracker() {
        return transactionTracker;
    }

    /**
     * Checks the status of the platform and notifies the SwirldMain if there is a change in status
     */
    void checkPlatformStatus() {
        final int numNodes = initialAddressBook.getSize();

        synchronized (currentPlatformStatus) {
            final PlatformStatus newStatus;
            if (numNodes > 1 && activeConnectionNumber.get() == 0) {
                newStatus = PlatformStatus.DISCONNECTED;
            } else if (getSyncManager().hasFallenBehind()) {
                newStatus = PlatformStatus.BEHIND;
            } else if (!startupThrottle.allNodesStarted()) {
                newStatus = PlatformStatus.STARTING_UP;
            } else if (freezeManager.isFreezeStarted()) {
                newStatus = PlatformStatus.MAINTENANCE;
            } else if (freezeManager.isFreezeComplete()) {
                newStatus = PlatformStatus.FREEZE_COMPLETE;
            } else {
                newStatus = PlatformStatus.ACTIVE;
            }

            final PlatformStatus oldStatus = currentPlatformStatus.getAndSet(newStatus);
            if (oldStatus != newStatus) {
                final PlatformStatus ns = newStatus;
                LOG.info(
                        PLATFORM_STATUS.getMarker(),
                        () ->
                                new PlatformStatusPayload(
                                                "Platform status changed.",
                                                oldStatus == null ? "" : oldStatus.name(),
                                                ns.name())
                                        .toString());

                LOG.info(
                        PLATFORM_STATUS.getMarker(),
                        "Platform status changed to: {}",
                        newStatus.toString());

                appMain.platformStatusChange(newStatus);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void newConnectionOpened(final Connection sc) {
        activeConnectionNumber.getAndIncrement();
        checkPlatformStatus();
        networkMetrics.connectionEstablished(sc);
    }

    /** {@inheritDoc} */
    @Override
    public void connectionClosed(final boolean outbound) {
        final int connectionNumber = activeConnectionNumber.decrementAndGet();
        if (connectionNumber < 0) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "activeConnectionNumber is {}, this is a bug!",
                    connectionNumber);
        }
        checkPlatformStatus();

        if (outbound) {
            platformMetrics.incrementInterruptedCallSyncs();
        } else {
            platformMetrics.incrementInterruptedRecSyncs();
        }
    }

    /**
     * @return the instance that manages interactions with the {@link SwirldState}
     */
    public SwirldStateManager getSwirldStateManager() {
        return swirldStateManager;
    }

    /**
     * @return the handler that applies consensus events to state and creates signed states
     */
    public ConsensusRoundHandler getConsensusHandler() {
        return consensusRoundHandler;
    }

    /**
     * @return the handler that applies pre-consensus events to state
     */
    public PreConsensusEventHandler getPreConsensusHandler() {
        return preConsensusEventHandler;
    }

    /** {@inheritDoc} */
    @Override
    public Console createConsole(final boolean visible) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        final Rectangle winRect = winRect();
        // if SwirldMain calls createConsole, this remembers the window created
        final Console console =
                new Console(getAddressBook().getAddress(selfId.getId()).getSelfName(), winRect);
        console.getWindow().setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        SwirldMenu.addTo(this, console.getWindow(), 40, Color.white, false);
        console.setVisible(true);
        return console;
    }

    /** {@inheritDoc} */
    @Override
    public boolean createTransaction(final byte[] trans) {
        return transactionSubmitter.submitTransaction(new SwirldTransaction(trans));
    }

    /** {@inheritDoc} */
    @Override
    public JFrame createWindow(final boolean visible) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        final Rectangle winRect = winRect();

        JFrame frame = null;
        try {
            final Address addr = getAddressBook().getAddress(selfId.getId());
            final String name = addr.getSelfName();
            frame = new JFrame(name); // create a new window

            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setBackground(Color.DARK_GRAY);
            frame.setSize(winRect.width, winRect.height);
            frame.setPreferredSize(new Dimension(winRect.width, winRect.height));
            frame.setLocation(winRect.x, winRect.y);
            SwirldMenu.addTo(this, frame, 40, Color.BLUE, false);
            frame.setFocusable(true);
            frame.requestFocusInWindow();
            frame.setVisible(visible); // show it
        } catch (final Exception e) {
            LOG.error(EXCEPTION.getMarker(), "", e);
        }

        return frame;
    }

    /** {@inheritDoc} */
    @Override
    public Instant estimateTime() {
        // Estimated consensus times are predicted only here and in Event.estimateTime().

        /* seconds from self creating an event to self creating the next event */
        double c2c = 1.0 / Math.max(0.5, addedEventMetrics.getEventsCreatedPerSecond());
        /* seconds from self creating an event to the consensus timestamp that event receives */
        double c2t = consensusMetrics.getAvgSelfCreatedTimestamp();

        // for now, just use 0. A more sophisticated formula could be used
        c2c = 0;
        c2t = 0;

        return Instant.now().plus((long) ((c2c / 2.0 + c2t) * 1_000_000_000.0), ChronoUnit.NANOS);
    }

    /** {@inheritDoc} */
    @Override
    public String getAbout() {
        return appAbout;
    }

    /** {@inheritDoc} */
    @Override
    public void setAbout(final String about) {
        appAbout = about;
    }

    /** {@inheritDoc} */
    @Override
    public Address getAddress() {
        if (selfId.isMirror()) {
            throw new RuntimeException("Mirror node not yet implemented!");
        }
        return initialAddressBook.getAddress(selfId.getId());
    }

    /** {@inheritDoc} */
    @Override
    public Address getAddress(final long id) {
        return getAddressBook().getAddress(id);
    }

    /** {@inheritDoc} */
    @Override
    public PlatformEvent[] getAllEvents() {
        final EventImpl[] allEvents = shadowGraph.getAllEvents();
        Arrays.sort(
                allEvents,
                (o1, o2) -> {
                    if (o1.getConsensusOrder() != -1 && o2.getConsensusOrder() != -1) {
                        // both are consensus
                        return Long.compare(o1.getConsensusOrder(), o2.getConsensusOrder());
                    } else if (o1.getConsensusTimestamp() == null
                            && o2.getConsensusTimestamp() == null) {
                        // neither are consensus
                        return o1.getTimeReceived().compareTo(o2.getTimeReceived());
                    } else {
                        // one is consensus, the other is not
                        if (o1.getConsensusTimestamp() == null) {
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                });
        return allEvents;
    }

    /** {@inheritDoc} */
    @Override
    public long getLastGen(final long creatorId) {
        return eventMapper.getHighestGenerationNumber(creatorId);
    }

    /** {@inheritDoc} */
    @Override
    public int getNumMembers() {
        return initialAddressBook.getSize();
    }

    /** {@inheritDoc} */
    @Override
    public String[] getParameters() {
        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public long getSleepAfterSync() {
        return delayAfterSync;
    }

    /** {@inheritDoc} */
    @Override
    public void setSleepAfterSync(final long delay) {
        delayAfterSync = delay;
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends SwirldState> T getState() {
        return (T) swirldStateManager.getCurrentSwirldState();
    }

    /** {@inheritDoc} */
    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    public long getRoundOfLastRecoveredEvent() {
        return roundOfLastRecoveredEvent;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getSwirldId() {
        return swirldId.clone();
    }

    /** {@inheritDoc} */
    @Override
    public void releaseState() {
        swirldStateManager.releaseCurrentSwirldState();
    }

    /** {@inheritDoc} */
    @Override
    public Signature sign(final byte[] data) {
        return crypto.sign(data);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends Metric> T getOrCreateMetric(final MetricConfig<T, ?> config) {
        CommonUtils.throwArgNull(config, "config");
        return metrics.getOrCreate(config);
    }

    /**
     * Get the Address Book
     *
     * @return AddressBook
     */
    public AddressBook getAddressBook() {
        return initialAddressBook;
    }

    /**
     * Get consensusTimestamp of the last signed state
     *
     * @return an {@code Instant} which identifies the consensus time stamp of the last signed
     *     state, or null if there have not yet been any signed states
     */
    @Override
    public Instant getLastSignedStateTimestamp() {
        try (final AutoCloseableWrapper<SignedState> wrapper =
                signedStateManager.getLastSignedState()) {
            if (wrapper.get() != null) {
                return wrapper.get().getConsensusTimestamp();
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLastCompleteSwirldState() {
        final AutoCloseableWrapper<SignedState> wrapper =
                signedStateManager.getLastCompleteSignedState(true);
        final SignedState state = wrapper.get();

        return new AutoCloseableWrapper<>(
                state == null ? null : (T) state.getSwirldState(), wrapper::close);
    }

    /**
     * @return the instance for calculating runningHash and writing event stream files
     */
    EventStreamManager getEventStreamManager() {
        return eventStreamManager;
    }

    /**
     * get the StartUpEventFrozenManager used by this platform
     *
     * @return The StartUpEventFrozenManager used by this platform
     */
    StartUpEventFrozenManager getStartUpEventFrozenManager() {
        return startUpEventFrozenManager;
    }

    /**
     * Initializes EventStreamManager instance, which will start threads for calculating
     * RunningHash, and writing event stream files when event streaming is enabled
     *
     * @param name name of this node
     */
    void initEventStreamManager(final String name) {
        try {
            LOG.info(STARTUP.getMarker(), "initialize eventStreamManager");
            eventStreamManager =
                    new EventStreamManager<>(
                            getSelfId(),
                            this,
                            name,
                            settings.isEnableEventStreaming(),
                            settings.getEventsLogDir(),
                            settings.getEventsLogPeriod(),
                            settings.getEventStreamQueueCapacity(),
                            this::isLastEventBeforeRestart);
        } catch (final NoSuchAlgorithmException | IOException e) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "Fail to initialize eventStreamHelper. Exception: {}",
                    ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * check whether the given event is the last event in its round, and the platform enters freeze
     * period, or whether this event is the last event before shutdown
     *
     * @param event a consensus event
     * @return whether this event is the last event to be added before restart
     */
    private boolean isLastEventBeforeRestart(final EventImpl event) {
        return (event.isLastInRoundReceived()
                        && swirldStateManager.isInFreezePeriod(event.getConsensusTimestamp()))
                || event.isLastOneBeforeShutdown();
    }

    void setGenesisFreezeTime(final long genesisFreezeTime) {
        this.genesisFreezeTime = genesisFreezeTime;
    }

    /**
     * @return the platform instance of the {@link ShadowGraphSynchronizer}
     */
    public ShadowGraphSynchronizer getShadowGraphSynchronizer() {
        return shadowgraphSynchronizer;
    }

    /**
     * @return the instance that throttles simultaneous syncs
     */
    public SimultaneousSyncThrottle getSimultaneousSyncThrottle() {
        return simultaneousSyncThrottle;
    }
}

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform;

import com.swirlds.common.Console;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.writer.MetricsWriterService;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.statistics.internal.AbstractStatistics;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
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
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.logging.payloads.RecoveredStateSavedPayload;
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
import com.swirlds.platform.components.SystemTransactionHandlerImpl;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRules;
import com.swirlds.platform.components.TransactionTracker;
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
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.internal.SignedStateLoadingException;
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
import com.swirlds.platform.reconnect.FallenBehindManagerImpl;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectProtocol;
import com.swirlds.platform.reconnect.ReconnectProtocolResponder;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.reconnect.SignedStateValidator;
import com.swirlds.platform.state.BackgroundHashChecker;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.DeserializedSignedState;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.stats.PerSecondStat;
import com.swirlds.platform.stats.StatConstructor;
import com.swirlds.platform.stats.simple.LongMetric;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.sync.Generations;
import com.swirlds.platform.sync.ShadowGraph;
import com.swirlds.platform.sync.ShadowGraphEventObserver;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SimultaneousSyncThrottle;
import com.swirlds.platform.sync.SyncProtocolResponder;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.threading.PauseAndClear;
import com.swirlds.platform.threading.PauseAndLoad;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.swirlds.common.io.utility.FileUtils.deleteDirectoryAndLog;
import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.common.utility.Units.SECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;
import static com.swirlds.platform.system.SystemExitReason.SAVED_STATE_NOT_LOADED;
import static com.swirlds.platform.system.SystemExitReason.SWIRLD_MAIN_THREW_EXCEPTION;
import static com.swirlds.platform.system.SystemUtils.exitSystem;

public class SwirldsPlatform extends AbstractPlatform {

	public static final String PLATFORM_THREAD_POOL_NAME = "platform-core";
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** alert threshold for java app pause */
	private static final long PAUSE_ALERT_INTERVAL = 5000;
	// The following hold info about the swirld being run (appMain), and the platform's id
	/**
	 * the ID of the member running this. Since a node can be a main node or a mirror node, the ID is not a primitive
	 * value
	 */
	protected final NodeId selfId;
	/**
	 * This object is responsible for rate limiting reconnect attempts (in the role of sender)
	 */
	private final ReconnectThrottle reconnectThrottle;
	/** A type used by Hashgraph, Statistics, and SyncUtils. Only Hashgraph modifies this type instance. */
	private final EventMapper eventMapper;
	/**
	 * A simpler event mapper used for chatter. Stores the thread-safe GossipEvent and has less functionality.
	 * The plan is for this to replace EventMapper once syncing is removed from the code
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
	 * The shadow graph manager. This wraps a shadow graph, which is an Event graph that
	 * adds child pointers to the Hashgraph Event graph. Used for gossiping.
	 */
	private final ShadowGraph shadowGraph;
	/** The last status of the platform that was determined, is null until the platform starts up */
	private final AtomicReference<PlatformStatus> currentPlatformStatus = new AtomicReference<>(null);
	/** the number of active connections this node has to other nodes */
	private final AtomicInteger activeConnectionNumber = new AtomicInteger(0);
	/**
	 * the object used to calculate consensus. it is volatile because the whole object is replaced when reading a state
	 * from disk or getting it through reconnect
	 */
	private final AtomicReference<Consensus> consensusRef;
	/** set in the constructor and given to the SwirldState object in run() */
	private final AddressBook initialAddressBook;
	private final SimultaneousSyncThrottle simultaneousSyncThrottle =
			new SimultaneousSyncThrottle(Settings.maxIncomingSyncsInc + Settings.maxOutgoingSyncs);
	/** all the events and other data about the hashgraph */
	protected EventTaskCreator eventTaskCreator;
	/** font size to use in the console text window */
	protected int fontSize;
	/** statistics to monitor the network, syncing, etc. */
	protected Statistics stats;
	/** Application statistics */
	protected ApplicationStatistics appStats;
	/** ID number of the swirld being run */
	protected byte[] swirldId;
	/** the object that contains all key pairs and CSPRNG state for this member */
	protected Crypto crypto;
	/**
	 * Set to true when state recovery is in progress.
	 */
	protected volatile boolean stateRecoveryInProgress;
	/** tell which pairs of members should establish connections */
	final NetworkTopology topology;
	/** the main program for the app. (The app also has a SwirldState, managed by eventFlow) */
	private SwirldMain appMain;
	/** a long name including (app, swirld, member id, member self name) */
	private String platformName;
	/** this Platform's infoMember, representing the (app, swirld ID, member ID) triplet it is running */
	private StateHierarchy.InfoMember infoMember;
	/** is used for calculating runningHash of all consensus events and writing consensus events to file */
	private EventStreamManager<EventImpl> eventStreamManager;
	/** the initial signed state that was loaded from disk on platform startup */
	private volatile SignedState signedState = null;
	/**
	 * The initial swirld state that was loaded from disk on platform startup, this gets passed to {@link
	 * SwirldStateManager}. Should only be accessed in synchronized blocks.
	 */
	private State initialState = null;

	/**
	 * The previous version of the software that was run. Null if this is the first time running, or if the previous
	 * version ran before the concept of application software versioning was introduced.
	 */
	private SoftwareVersion previousSoftwareVersion;

	/** Reference to the instance responsible for executing reconnect when chatter is used */
	private final AtomicReference<ReconnectController> reconnectController = new AtomicReference<>();
	/** Helps when executing a reconnect */
	private ReconnectHelper reconnectHelper;
	/** tracks if we have fallen behind or not, takes appropriate action if we have */
	private final FallenBehindManagerImpl fallenBehindManager;
	/** tells callers who to sync with and keeps track of whether we have fallen behind */
	private SyncManagerImpl syncManager;
	/** locks used to synchronize usage of outbound connections */
	private SharedConnectionLocks sharedConnectionLocks;
	/** a thread that writes, reads and deletes FastCopyable object to/from files */
	private SignedStateFileManager signedStateFileManager;
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
	/** stats related to the intake cycle */
	private final IntakeCycleStats intakeCycleStats;
	private QueueThread<EventIntakeTask> intakeQueue;
	private EventLinker eventLinker;
	private final LongMetric orphanBufferSize = new LongMetric(
			"intake",
			"numOrphans",
			"the number of events without parents buffered"
	);
	private SequenceCycle<EventIntakeTask> intakeCycle = null;
	/** sleep in ms after each sync in SyncCaller. A public setter for this exists. */
	private long delayAfterSync = 0;
	/**
	 * freezeTime in seconds;
	 * when the node is started from genesis, and this value is positive,
	 * set this node's freezeTime to be an Instant with this many epoch seconds
	 */
	private long genesisFreezeTime = -1;
	/** Executes a sync with a remote node */
	private ShadowGraphSynchronizer shadowgraphSynchronizer;
	private final ChatterCore<GossipEvent> chatterCore;

	/** Stores and passes pre-consensus events to {@link SwirldStateManager} for handling */
	private PreConsensusEventHandler preConsensusEventHandler;

	/** Stores and processes consensus events including sending them to {@link SwirldStateManager} for handling */
	private ConsensusRoundHandler consensusRoundHandler;

	/** Handles all interaction with {@link SwirldState} */
	private SwirldStateManager swirldStateManager;

	/** Checks the validity of transactions and submits valid ones to the event transaction pool */
	private TransactionSubmitter transactionSubmitter;

	/** clears all pipelines to prepare for a reconnect */
	private Clearables clearAllPipelines;

	/**
	 * the browser gives the Platform what app to run. There can be multiple Platforms on one computer.
	 *
	 * @param winNum
	 * 		this is the Nth copy of the Platform running on this machine (N=winNum)
	 * @param parameters
	 * 		parameters given to the Platform at the start, for app to use
	 * @param crypto
	 * 		an object holding all the public/private key pairs and the CSPRNG state for this member
	 * @param swirldId
	 * 		the ID of the swirld being run
	 * @param id
	 * 		the ID number for this member (if this computer has multiple members in one swirld)
	 * @param initialAddressBook
	 * 		the address book listing all members in the community
	 * @param fontSize
	 * 		a recommended font size for windows
	 * @param mainClassName
	 * 		the name of the app class inheriting from SwirldMain
	 * @param swirldName
	 * 		the name of the swirld being run
	 * @param appLoader
	 * 		the object used to load the user app
	 */
	SwirldsPlatform(final int winNum, final String[] parameters, final Crypto crypto, final byte[] swirldId,
			final NodeId id, final AddressBook initialAddressBook, final int fontSize,
			final String mainClassName, final String swirldName, final SwirldAppLoader appLoader) {

		// ThreadDumpGenerator.createDeadlock(); // intentionally deadlock, for debugging
		this.mainClassName = mainClassName;
		this.swirldName = swirldName;
		try {
			this.appMain = appLoader.instantiateSwirldMain();
		} catch (final Exception e) {
			CommonUtils.tellUserConsolePopup("ERROR", "ERROR: There are problems starting class " +
					mainClassName + "\n" + ExceptionUtils.getStackTrace(e));
			log.error(EXCEPTION.getMarker(), "Problems with class {}", mainClassName, e);
		}

		this.winNum = winNum;
		this.parameters = parameters;
		// the memberId of the member running this Platform object
		this.selfId = id;
		// set here, then given to the state in run(). A copy of it is given to hashgraph.
		this.initialAddressBook = initialAddressBook;

		this.eventMapper = new EventMapper(selfId);
		this.chatterEventMapper = new ChatterEventMapper();

		final List<Metric> additionalStats = new ArrayList<>();
		final List<PerSecondStat> perSecondStats = new ArrayList<>();
		if (Settings.chatter.isChatterUsed()) {
			chatterCore = new ChatterCore<>(
					GossipEvent.class,
					new PrepareChatterEvent(CryptoFactory.getInstance()),
					Settings.chatter,
					(peerId, pingNanos) -> {
						// because the Statistics are constructed after chatter, we need this additional step
						final Statistics s = this.getStats();
						if (s != null) {
							s.recordPingTime(peerId, pingNanos);
						}
					}
			);
			additionalStats.addAll(chatterCore.getStats());
			additionalStats.add(orphanBufferSize);
			perSecondStats.addAll(chatterCore.getPerSecondStats());
		} else {
			chatterCore = null;
		}
		additionalStats.add(
				StatConstructor.createEnumStat(
						"PlatformStatus",
						AbstractStatistics.CATEGORY,
						PlatformStatus.values(),
						currentPlatformStatus::get
				)
		);
		this.intakeCycleStats = new IntakeCycleStats();
		additionalStats.addAll(intakeCycleStats.getAllEntries());
		this.stats = new Statistics(this, additionalStats, perSecondStats);

		this.shadowGraph = new ShadowGraph(stats, initialAddressBook.getSize());

		this.fontSize = fontSize;

		this.consensusRoundHandler = null;
		this.swirldId = swirldId.clone();
		this.crypto = crypto;
		this.appStats = new ApplicationStatistics();

		startUpEventFrozenManager = new StartUpEventFrozenManager(this::getStats, Instant::now);
		freezeManager = new FreezeManager(this::checkPlatformStatus);

		if (Settings.state.getSaveStatePeriod() > 0
				|| Settings.state.dumpStateOnISS
				|| Settings.state.dumpStateOnFatal) {
			signedStateFileManager = new SignedStateFileManager(
					mainClassName,
					selfId,
					swirldName,
					freezeManager::freezeComplete);
		}

		signedStateManager = new SignedStateManager(this,
				PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
				signedStateFileManager, freezeManager::freezeEventCreation,
				Settings.state);

		if (Settings.state.backgroundHashChecking) {
			// This object performs background sanity checks on copies of the state.
			new BackgroundHashChecker(signedStateManager::getLastCompleteSignedState);
		}

		systemTransactionHandler = new SystemTransactionHandlerImpl(selfId, signedStateManager);

		consensusRef = new AtomicReference<>();

		reconnectThrottle = new ReconnectThrottle(Settings.reconnect);

		topology = new StaticTopology(
				selfId,
				initialAddressBook.getSize(),
				Settings.numConnections,
				!Settings.chatter.isChatterUsed()
		);

		fallenBehindManager = new FallenBehindManagerImpl(
				selfId,
				topology.getConnectionGraph(),
				this::checkPlatformStatus,
				() -> {
					if (!Settings.chatter.isChatterUsed()) {
						return;
					}
					reconnectController.get().start();
				},
				Settings.reconnect
		);
	}

	/**
	 * Checks if this platform is starting from genesis or a saved state.
	 *
	 * NOTE: this check will not work (return false) until {@link #loadSavedStateFromDisk()} is called
	 *
	 * @return true if we are starting from genesis, false if we are starting from a saved state
	 */
	private boolean startingFromGenesis() {
		return signedState == null;
	}

	/**
	 * Get the transactionMaxBytes in Settings
	 *
	 * @return integer representing the maximum number of bytes allowed in a transaction
	 */
	public static int getTransactionMaxBytes() {
		return Settings.transactionMaxBytes;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getSwirldName() {
		return swirldName;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeId getSelfId() {
		return selfId;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isMirrorNode() {
		return selfId.isMirror();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Cryptography getCryptography() {
		return CryptoFactory.getInstance();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	String getPlatformName() {
		// this will be the empty string until the Browser calls setInfoMember. Then it will be correct.
		return platformName;
	}

	@Override
	public String getMainClassName() {
		return mainClassName;
	}

	/**
	 * Get the platform name, which represents a particular (swirld ID, member ID) pair.
	 *
	 * @return the platform name
	 */
	StateHierarchy.InfoMember getInfoMember() {
		return infoMember;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setInfoMember(final StateHierarchy.InfoMember infoMember) {
		this.infoMember = infoMember;
		this.platformName = infoMember.name
				+ " - " + infoMember.swirld.name
				+ " - " + infoMember.swirld.app.name;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean createSystemTransaction(final SystemTransaction systemTransaction) {
		return swirldStateManager.submitTransaction(systemTransaction);
	}

	/**
	 * Load the state from the disk if it is present.
	 *
	 * @throws SignedStateLoadingException
	 * 		if an exception is encountered while loading the state
	 */
	synchronized boolean loadSavedStateFromDisk() throws SignedStateLoadingException {

		final SavedStateInfo[] savedStateFiles = getSavedStateFiles(mainClassName, selfId, swirldName);
		if (savedStateFiles == null || savedStateFiles.length == 0) {
			if (Settings.requireStateLoad) {
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

					// When loading from disk, we should hash the state every time so that the first fast copy will
					// only hash the difference
					final Hash newHash = rehashTree(signedState.getState());

					if (Settings.checkSignedStateFromDisk) {
						if (newHash.equals(oldHash)) {
							log.info(STARTUP.getMarker(), "Signed state loaded from disk has a valid hash.");
						} else {
							log.error(STARTUP.getMarker(),
									"ERROR: Signed state loaded from disk has an invalid hash!\ndisk:{}\ncalc:{}",
									oldHash, newHash);
						}
					}

					log.info(STARTUP.getMarker(), "Information for state loaded from disk:\n{}\n{}",
							() -> signedState.getState().getPlatformState().getInfoString(),
							() -> generateHashDebugString(signedState.getState(), StateSettings.getDebugHashDepth()));

					previousSoftwareVersion =
							signedState.getState().getPlatformState().getPlatformData().getCreationSoftwareVersion();
					initialState = signedState.getState().copy();
					initialState.getPlatformState().getPlatformData().setCreationSoftwareVersion(
							appMain.getSoftwareVersion());

					initialState.getSwirldState().init(
							this,
							initialAddressBook.copy(),
							initialState.getSwirldDualState(),
							InitTrigger.RESTART,
							previousSoftwareVersion);

					signedStateManager.addCompleteSignedState(signedState, true);

					loadedSavedState = true;
				} catch (final Exception e) {
					signedState = null;
					throw new SignedStateLoadingException("Exception while reading signed state!", e);
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
	 * {@inheritDoc}
	 */
	@Override
	void loadIntoConsensusAndEventMapper(final SignedState signedState) {
		consensusRef.set(new ConsensusImpl(
				this::getStats,
				consensusRoundHandler::addMinGenInfo,
				getAddressBook(),
				signedState));

		shadowGraph.initFromEvents(
				EventUtils.prepareForShadowGraph(
						// ConsensusImpl will filter out events that are not insertable,
						// so we get only the insertable ones
						consensusRef.get().getAllEvents()
				),
				// we need to provide the minGen from consensus so that expiry matches after a restart/reconnect
				consensusRef.get().getMinRoundGeneration());

		// Data that is needed for the intake system to work
		for (final EventImpl e : signedState.getEvents()) {
			eventMapper.eventAdded(e);
		}

		if (Settings.chatter.isChatterUsed()) {
			chatterEventMapper.loadFromSignedState(signedState);
		}

		transactionTracker.reset();

		log.info(STARTUP.getMarker(), "Last known events after restart are {}",
				eventMapper.getMostRecentEventsByEachCreator());
	}

	/**
	 * Used to load the state received from the sender.
	 *
	 * @param signedState
	 * 		the signed state that was received from the sender
	 */
	void loadReconnectState(final SignedState signedState) {
		// the state was received, so now we load its data into different objects
		log.debug(RECONNECT.getMarker(), "`loadReconnectState` : reloading state");
		try {
			// Make sure the signature set in the signed state is big enough for the current address book
			signedState.expandSigSetIfNeeded(initialAddressBook);

			// It's important to call init() before loading the signed state. The loading process makes copies
			// of the state, and we want to be sure that the first state in the chain of copies has been initialized.
			signedState.getSwirldState().init(
					this,
					initialAddressBook.copy(),
					signedState.getState().getSwirldDualState(),
					InitTrigger.RECONNECT,
					signedState.getState().getPlatformState().getPlatformData().getCreationSoftwareVersion());

			swirldStateManager.loadFromSignedState(signedState);
			getSignedStateManager().addCompleteSignedState(signedState, false);
			loadIntoConsensusAndEventMapper(signedState);
			// eventLinker is not thread safe, which is not a problem regularly because it is only used by a single
			// thread. after a reconnect, it needs to load the minimum generation from a state on a different thread,
			// so the intake thread is paused before the data is loaded and unpaused after. this ensures that the
			// thread will get the up-to-date data loaded
			new PauseAndLoad(getIntakeQueue(), eventLinker).loadFromSignedState(signedState);
			getConsensusHandler().loadDataFromSignedState(signedState, true);

			// Notify any listeners that the reconnect has been completed
			try {
				signedState.reserveState();
				NotificationFactory.getEngine().dispatch(
						ReconnectCompleteListener.class,
						new ReconnectCompleteNotification(signedState.getLastRoundReceived(),
								signedState.getConsensusTimestamp(), signedState.getState().getSwirldState()));
			} finally {
				signedState.releaseState();
			}
		} catch (final RuntimeException e) {
			log.debug(RECONNECT.getMarker(), "`loadReconnectState` : FAILED, reason: {}", e.getMessage());
			// if the loading fails for whatever reason, we clear all data again in case some of it has been loaded
			clearAllPipelines.clear();
			throw e;
		}

		log.debug(RECONNECT.getMarker(),
				"`loadReconnectState` : reconnect complete notifications finished. Resetting fallen-behind");
		getSyncManager().resetFallenBehind();
		log.debug(RECONNECT.getMarker(),
				"`loadReconnectState` : resetting fallen-behind & reloading state, finished, succeeded`");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isStateRecoveryInProgress() {
		return stateRecoveryInProgress;
	}

	/**
	 * Recover signed state from event streaming files
	 */
	private void stateRecover() {
		log.info(EVENT_PARSER.getMarker(), "State recover process started");

		if (signedState == null) {
			log.error(EXCEPTION.getMarker(), "No saved state can be used for recover process");
			exitSystem(SAVED_STATE_NOT_LOADED);
		}
		Settings.state.setEnableStateRecovery(true);
		stateRecoveryInProgress = true;

		final Instant loadedLastTimestamp = signedState.getConsensusTimestamp();
		log.info(EVENT_PARSER.getMarker(), "Last timestamp from loaded state is {} of round {}",
				loadedLastTimestamp,
				signedState.getLastRoundReceived());
		Instant endTimeStamp = Instant.MAX;
		if (!Settings.playbackEndTimeStamp.isEmpty()) {
			try {
				endTimeStamp = Instant.parse(Settings.playbackEndTimeStamp);
			} catch (final DateTimeParseException e) {
				log.info(EXCEPTION.getMarker(), "Parsing playbackEndTimeStamp error ", e);
			}
		}

		// the round number of last signed state loaded from disk
		final long roundOfLoadedSignedState = signedState.getLastRoundReceived();

		//run parser on a different thread, so platform can poll and insert to forCons at the same time
		// to avoid memory failure if there were huge amount of events to be loaded
		final String name;
		if (this.getAddress().getMemo() != null && !this.getAddress().getMemo().isEmpty()) {
			name = this.getAddress().getMemo();
		} else {
			name = String.valueOf(selfId);
		}

		final StreamEventParser streamEventParser = new StreamEventParser(
				Settings.playbackStreamFileDirectory + "/events_" + name,
				loadedLastTimestamp,
				endTimeStamp,
				eventStreamManager);
		streamEventParser.start();


		NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, (notification) -> {
			// in recover mode, once we saved the last recovered signed state we can exit recover mode
			if (notification.getRoundNumber() >= roundOfLastRecoveredEvent) {
				log.info(EVENT_PARSER.getMarker(), () -> new RecoveredStateSavedPayload(
						"Last recovered signed state has been saved in state recover mode.",
						notification.getRoundNumber()).toString());
				// sleep 10 secs to let event stream finish writing the last file
				try {
					Thread.sleep(10 * SECONDS_TO_MILLISECONDS);
				} catch (final InterruptedException e) {
					log.error(EXCEPTION.getMarker(), "could not sleep", e);
					Thread.currentThread().interrupt();
				}
				exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
			}
		});

		long forConsQueueCount = 0;
		long addForConsEventsCount = 0;
		long minGen = Long.MAX_VALUE;
		EventImpl event;
		EventImpl prevRecoveredEvent = null;

		// must use sorted keys to extract values from map, otherwise eventsInRound
		// could be added to consensusRoundHandler out of order.
		// for example, round 101 added first, then round 100 added later
		final SortedMap<Long, List<EventImpl>> recoveredEventsByRound = new TreeMap<>();
		do {
			//poll event from streamEventParser
			event = streamEventParser.getNextEvent();
			if (event != null) {
				// force timeReceived value to a deterministic value
				// to guarantee that if run recover mode multiple times,
				// generated signed state binary files are the same
				event.setTimeReceived(event.getTimeCreated().plusNanos(1));

				if (event.getGeneration() < minGen) { //search min generation value among those events
					minGen = event.getGeneration();
				}

				addForConsEventsCount++;

				if (event.isLastInRoundReceived()) {
					if (event.hasUserTransactions()) {
						transactionTracker.setLastRRwithUserTransaction(event.getRoundReceived());
					}

					//manually update minGenInfo
					consensusRoundHandler.addMinGenInfo(event.getRoundReceived(), minGen);
					minGen = Long.MAX_VALUE;
				}

				// whenever we polled an event from parser, we don't know whether it will be the last
				// recovered event, so we save it to placeholder, and push the previous recovered event
				// to forCons queue
				if (prevRecoveredEvent != null) {
					recoveredEventsByRound.putIfAbsent(prevRecoveredEvent.getRoundReceived(), new LinkedList<>());
					recoveredEventsByRound.get(prevRecoveredEvent.getRoundReceived()).add(prevRecoveredEvent);
					forConsQueueCount++;
				}
				prevRecoveredEvent = event;
			}
		} while (!streamEventParser.noMoreEvents());

		//if finish extracting events from streamParser, then prevRecoveredEvent is the last recovered event
		// we set its isLastEventBeforeShutdown to be true,
		// so that event stream file will be closed after writing this event
		if (prevRecoveredEvent != null) {
			if (!prevRecoveredEvent.isLastInRoundReceived()) {
				log.info(EVENT_PARSER.getMarker(), "Last recovered event is not last event of its round {}",
						prevRecoveredEvent.getRoundReceived());
				transactionTracker.setLastRRwithUserTransaction(roundOfLoadedSignedState);
				//manually update minGenInfo
				consensusRoundHandler.addMinGenInfo(prevRecoveredEvent.getRoundReceived(), minGen);
			}
			prevRecoveredEvent.setLastOneBeforeShutdown(true);
			recoveredEventsByRound.putIfAbsent(prevRecoveredEvent.getRoundReceived(), new LinkedList<>());
			recoveredEventsByRound.get(prevRecoveredEvent.getRoundReceived()).add(prevRecoveredEvent);
			forConsQueueCount++;
			// only update roundOfLastRecoveredEvent when all events have been recovered from even files
			roundOfLastRecoveredEvent = prevRecoveredEvent.getRoundReceived();
		}

		// Feed all the recovered events to the consensus event handler in rounds
		for (final List<EventImpl> eventsInRound : recoveredEventsByRound.values()) {
			consensusRoundHandler.consensusRound(
					new ConsensusRound(
							eventsInRound,
							Generations.GENESIS_GENERATIONS // unused by recovery
					)
			);
		}

		if (addForConsEventsCount != streamEventParser.getEventsCounter()) {
			log.error(EXCEPTION.getMarker(),
					"Error : Number of events recovered not equal to added to forCons {}",
					addForConsEventsCount);
		}

		// update dual state status to clear any possible inconsistency between freezeTime and lastFrozenTime
		swirldStateManager.clearFreezeTimes();

		log.info(EVENT_PARSER.getMarker(), "Inserted {} event to forCons queue", addForConsEventsCount);
		log.info(EVENT_PARSER.getMarker(), "forConsQueueCount {}", forConsQueueCount);
		log.info(EVENT_PARSER.getMarker(), "lastRecoverRoundWithNewUserTran {}", roundOfLoadedSignedState);
		log.info(EVENT_PARSER.getMarker(), "roundOfLastRecoveredEvent {}", roundOfLastRecoveredEvent);

		if (addForConsEventsCount == 0) {
			log.info(EVENT_PARSER.getMarker(), "No event recovered from event files");
			exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
		}
	}

	/**
	 * First part of initialization. This was split up so that appMain.init() could be called before {@link
	 * StateLoadedFromDiskNotification} would be dispatched. Eventually, this should be split into more discrete
	 * parts.
	 */
	synchronized void initializeFirstStep() {

		// if this setting is 0 or less, there is no startup freeze
		if (Settings.freezeSecondsAfterStartup > 0) {
			final Instant startUpEventFrozenEndTime = Instant.now()
					.plusSeconds(Settings.freezeSecondsAfterStartup);
			startUpEventFrozenManager.setStartUpEventFrozenEndTime(startUpEventFrozenEndTime);
			log.info(STARTUP.getMarker(), "startUpEventFrozenEndTime: {}", () -> startUpEventFrozenEndTime);
		}

		// initializes EventStreamManager instance
		final Address address = this.getAddress();
		if (address.getMemo() != null && !address.getMemo().isEmpty()) {
			initEventStreamManager(address.getMemo());
		} else {
			initEventStreamManager(String.valueOf(selfId));
		}

		buildEventHandlers();

		transactionSubmitter = new TransactionSubmitter(
				currentPlatformStatus::get,
				PlatformConstructor.settingsProvider(),
				isZeroStakeNode(),
				swirldStateManager::submitTransaction,
				stats);

		if (Settings.waitAtStartup) {
			this.startupThrottle = new StartupThrottle(
					initialAddressBook,
					selfId,
					this::checkPlatformStatus,
					Settings.enableBetaMirror
			);
		} else {
			this.startupThrottle = StartupThrottle.getNoOpInstance();
		}


		this.transactionTracker = new TransactionTracker();

		if (signedState != null) {
			loadIntoConsensusAndEventMapper(signedState);
		} else {
			consensusRef.set(new ConsensusImpl(
					this::getStats,
					consensusRoundHandler::addMinGenInfo,
					getAddressBook()
			));
		}

		criticalQuorum = new CriticalQuorumImpl(initialAddressBook);

		// build the event intake classes
		buildEventIntake();
		if (signedState != null) {
			eventLinker.loadFromSignedState(signedState);
		}
		intakeQueue.start();

		// start all threads managing the queues.
		startEventHandlers();

		try {
			appMain.init(this, selfId);
		} catch (final Exception e) {
			log.error(EXCEPTION.getMarker(), "Exception while calling {}.init! Exiting...", mainClassName, e);
			com.swirlds.platform.system.SystemUtils.exitSystem(SWIRLD_MAIN_THREW_EXCEPTION);
		}

		clearAllPipelines = Clearables.of(
				getIntakeQueue(),
				getEventMapper(),
				getShadowGraph(),
				preConsensusEventHandler,
				consensusRoundHandler,
				swirldStateManager);
	}

	/**
	 * Creates and wires up all the classes responsible for accepting events from gossip, creating new events, and
	 * routing those events throughout the system.
	 */
	private void buildEventIntake() {
		final EventObserverDispatcher dispatcher = new EventObserverDispatcher(
				new ShadowGraphEventObserver(shadowGraph),
				consensusRoundHandler,
				preConsensusEventHandler,
				eventMapper,
				stats,
				startupThrottle,
				transactionTracker,
				criticalQuorum
		);
		if (Settings.chatter.isChatterUsed()) {
			dispatcher.addObserver(new ChatterNotifier(selfId, chatterCore));
			dispatcher.addObserver(chatterEventMapper);
		}

		final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);

		final List<Predicate<ChatterEventDescriptor>> isDuplicateChecks = new ArrayList<>();
		isDuplicateChecks.add(d -> shadowGraph.isHashInGraph(d.getHash()));
		if (Settings.chatter.isChatterUsed()) {
			final OrphanBufferingLinker orphanBuffer = new OrphanBufferingLinker(parentFinder);
			orphanBufferSize.setSupplier(orphanBuffer::getNumOrphans);
			eventLinker = orphanBuffer;
			// when using chatter an event could be an orphan, in this case it will be stored in the orphan set
			// when its parents are found, or become ancient, it will move to the shadowgraph
			// non-orphans are also stored in the shadowgraph
			// to dedupe, we need to check both
			isDuplicateChecks.add(orphanBuffer::isOrphan);
		} else {
			eventLinker = new InOrderLinker(parentFinder, eventMapper::getMostRecentEvent);
		}

		final EventIntake eventIntake = new EventIntake(
				selfId,
				eventLinker,
				consensusRef::get,
				initialAddressBook,
				dispatcher,
				intakeCycleStats
		);

		final EventCreator eventCreator;
		if (Settings.chatter.isChatterUsed()) {
			// chatter has a separate event creator in a different thread. having 2 event creators creates the risk
			// of forking, so a NPE is preferable to a fork
			eventCreator = null;
		} else {
			eventCreator = new EventCreator(
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
		// it is very important to discard ancient events, otherwise the deduplication will not work, since it doesn't
		// track ancient events
		validators.add(new AncientValidator(consensusRef::get));
		validators.add(new EventDeduplication(isDuplicateChecks, stats));
		validators.add(StaticValidators::isParentDataValid);
		if (Settings.enableBetaMirror) {
			validators.add(new ZeroStakeValidator(initialAddressBook));
		}
		validators.add(new TransactionSizeValidator(Settings.maxTransactionBytesPerEvent));
		if (Settings.verifyEventSigs) {
			validators.add(new SignatureValidator(initialAddressBook));
		}
		final GossipEventValidators eventValidators = new GossipEventValidators(validators);

		/* validates events received from gossip */
		final EventValidator eventValidator = new EventValidator(
				eventValidators,
				eventIntake::addUnlinkedEvent
		);

		final EventTaskDispatcher taskDispatcher = new EventTaskDispatcher(
				eventValidator,
				eventCreator,
				eventIntake::addUnlinkedEvent,
				stats,
				intakeCycleStats
		);

		final InterruptableConsumer<EventIntakeTask> intakeHandler;
		if (Settings.chatter.isChatterUsed()) {
			intakeCycle = new SequenceCycle<>(taskDispatcher::dispatchTask);
			intakeHandler = intakeCycle;
		} else {
			intakeHandler = taskDispatcher::dispatchTask;
		}
		intakeQueue = new QueueThreadConfiguration<EventIntakeTask>()
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("event-intake")
				.setHandler(intakeHandler)
				.setCapacity(Settings.eventIntakeQueueSize)
				.build();
	}

	private void startEventHandlers() {
		consensusRoundHandler.start();
		preConsensusEventHandler.start();
	}

	/**
	 * Build all the classes required for events and transactions to flow through the system
	 */
	private void buildEventHandlers() {
		// Queue thread that stores and handles signed states that need to be hashed and have signatures collected.
		final QueueThread<SignedState> stateHashSignQueueThread = PlatformConstructor.stateHashSignQueue(
				selfId.getId(),
				signedStateManager,
				stats
		);
		stateHashSignQueueThread.start();

		if (signedState != null) {
			log.debug(STARTUP.getMarker(), () -> new SavedStateLoadedPayload(
					signedState.getLastRoundReceived(),
					signedState.getConsensusTimestamp(),
					startUpEventFrozenManager.getStartUpEventFrozenEndTime())
					.toString());


			// migration from state which doesn't contain DualState child
			// we should initialize a DualState and set it
			if (initialState.getPlatformDualState() == null) {
				initialState.setDualState(new DualStateImpl());
			}
			buildEventHandlersFromState(initialState, stateHashSignQueueThread);

			consensusRoundHandler.loadDataFromSignedState(signedState, false);
		} else {
			final State state = newState();
			buildEventHandlersFromState(state, stateHashSignQueueThread);

			// if we are not starting from a saved state, don't freeze on startup
			startUpEventFrozenManager.setStartUpEventFrozenEndTime(null);
		}
	}

	private void buildEventHandlersFromState(final State state,
			final QueueThread<SignedState> stateHashSignQueueThread) {

		swirldStateManager = PlatformConstructor.swirldStateManager(selfId, systemTransactionHandler, stats,
				PlatformConstructor.settingsProvider(), this::estimateTime, freezeManager::isFreezeStarted, state);

		// SwirldStateManager will get a copy of the state loaded, that copy will become stateCons.
		// The original state will be saved in the SignedStateMgr and will be deleted when it becomes old
		preConsensusEventHandler = PlatformConstructor.preConsensusEventHandler(selfId, swirldStateManager, stats);
		consensusRoundHandler = PlatformConstructor.consensusHandler(
				selfId.getId(),
				PlatformConstructor.settingsProvider(),
				swirldStateManager,
				stats,
				eventStreamManager,
				initialAddressBook,
				stateHashSignQueueThread,
				freezeManager::freezeStarted,
				appMain.getSoftwareVersion());
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
		state.getSwirldState().init(
				this,
				initialAddressBook.copy(),
				state.getSwirldDualState(),
				InitTrigger.GENESIS,
				SoftwareVersion.NO_VERSION);

		return state;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void run() {
		if (Thread.getDefaultUncaughtExceptionHandler() == null) {
			// If there is no default uncaught exception handler already provided, make sure we set one to avoid threads
			// silently dying from exceptions.
			Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) ->
					log.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e));
		}

		syncManager = new SyncManagerImpl(
				intakeQueue,
				topology.getConnectionGraph(),
				selfId,
				new EventCreationRules(
						List.of(selfId,
								swirldStateManager.getTransactionPool(),
								startUpEventFrozenManager,
								freezeManager)
				),
				List.of(freezeManager, startUpEventFrozenManager),
				new TransThrottleSyncAndCreateRules(
						List.of(swirldStateManager.getTransactionPool(),
								swirldStateManager,
								startupThrottle)
				),
				signedStateManager::getLastRoundSavedToDisk,
				signedStateManager::getLastCompleteRound,
				transactionTracker,
				criticalQuorum,
				initialAddressBook,
				fallenBehindManager);

		this.eventTaskCreator = new EventTaskCreator(
				eventMapper,
				// hashgraph and state get separate copies of the address book
				initialAddressBook.copy(),
				selfId,
				this.stats,
				intakeQueue,
				StaticSettingsProvider.getSingleton(),
				syncManager,
				ThreadLocalRandom::current);

		// a genesis event could be created here, but it isn't needed. This member will naturally create an
		// event after their first sync, where the first sync will involve sending no events.

		if (Settings.statUpdatePeriod > 0) {// if -1, then never update, don't create the thread
			/* a Timer with one thread that calls stats.updateOthers() once a second */
			final Timer statTimer = new Timer("stat timer", true);
			statTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					stats.updateOthers();
					appStats.updateOthers();
				}
			}, 0, Settings.statUpdatePeriod); // update statistics periodically, starting now
		}

		final Thread mainThread = new ThreadConfiguration()
				.setPriority(Settings.threadPriorityNonSync)
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("appMainRun")
				.setRunnable(appMain)
				.build();

		shadowgraphSynchronizer = new ShadowGraphSynchronizer(
				getShadowGraph(),
				getNumMembers(),
				getStats(),
				consensusRef::get,
				eventTaskCreator::syncDone,
				eventTaskCreator::addEvent,
				syncManager,
				PlatformConstructor.parallelExecutor(),
				PlatformConstructor.settingsProvider(),
				true,
				() -> {
				}
		);

		final Runnable stopGossip = Settings.chatter.isChatterUsed()
				? chatterCore::stopChatter
				// wait and acquire all sync ongoing locks and release them immediately
				// this will ensure any ongoing sync are finished before we start reconnect
				// no new sync will start because we have a fallen behind status
				: getSimultaneousSyncThrottle()::waitForAllSyncsToFinish;
		reconnectHelper = new ReconnectHelper(
				stopGossip,
				clearAllPipelines,
				getSwirldStateManager()::getConsensusState,
				getSignedStateManager()::getLastCompleteRound,
				new ReconnectLearnerThrottle(selfId, Settings.reconnect),
				this::loadReconnectState,
				new ReconnectLearnerFactory(
						initialAddressBook,
						new SignedStateValidator(getCrypto()),
						Settings.reconnect,
						getStats().getReconnectStats()
				)
		);
		if (Settings.chatter.isChatterUsed()) {
			reconnectController.set(new ReconnectController(reconnectHelper, chatterCore::startChatter));
		}

		//In recover mode, skip sync with other nodes,
		// and don't start main to accept any new transactions
		if (!Settings.enableStateRecovery) {
			mainThread.start();
			if (Settings.chatter.isChatterUsed()) {
				startChatterNetwork();
			} else {
				startSyncNetwork();
			}
		}

		// Note: Temporary solution until management of metrics was cleaned up
		final List<Metric> metrics = new ArrayList<>(getStats().getMetrics());
		metrics.addAll(CryptoStatistics.getInstance().getMetrics());
		if (getAppStats() != null) {
			metrics.addAll(getAppStats().getMetrics());
		}
		final MetricsWriterService writerService = new MetricsWriterService(metrics, selfId);
		writerService.start();

		if (Settings.runPauseCheckTimer) {
			// periodically check current time stamp to detect whether the java application
			// has been paused for a long period
			final Timer pauseCheckTimer = new Timer("pause check", true);
			pauseCheckTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					final long currentTimeStamp = System.currentTimeMillis();
					if ((currentTimeStamp - pauseCheckTimeStamp) > PAUSE_ALERT_INTERVAL && pauseCheckTimeStamp != 0) {
						log.error(EXCEPTION.getMarker(), "ERROR, a pause larger than {} is detected ",
								PAUSE_ALERT_INTERVAL);
					}
					pauseCheckTimeStamp = currentTimeStamp;
				}
			}, 0, PAUSE_ALERT_INTERVAL / 2);
		}

		if (Settings.enableStateRecovery) {
			stateRecover();
		}

		synchronized (this) {
			if (signedState != null) {
				signedState = null;// we won't need this after this point
				initialState = null;// we won't need this after this point
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
	 * Construct and start all networking components that are common to both types of networks, sync and chatter.
	 * This is everything related to creating and managing connections to neighbors.Only the connection managers are
	 * returned since this should be the only point of entry for other components.
	 *
	 * @return an instance that maintains connection managers for all connections to neighbors
	 */
	public StaticConnectionManagers startCommonNetwork() {
		final SocketFactory socketFactory = PlatformConstructor.socketFactory(crypto.getKeysAndCerts());
		// create an instance that can create new outbound connections
		final OutboundConnectionCreator connectionCreator = new OutboundConnectionCreator(
				selfId,
				StaticSettingsProvider.getSingleton(),
				this,
				socketFactory,
				initialAddressBook
		);
		final StaticConnectionManagers connectionManagers = new StaticConnectionManagers(
				topology,
				connectionCreator
		);
		final InboundConnectionHandler inboundConnectionHandler = new InboundConnectionHandler(
				this,
				selfId,
				initialAddressBook,
				connectionManagers::newConnection,
				StaticSettingsProvider.getSingleton()
		);
		// allow other members to create connections to me
		final Address address = getAddressBook().getAddress(selfId.getId());
		final ConnectionServer connectionServer = new ConnectionServer(address.getListenAddressIpv4(),
				address.getListenPortIpv4(), socketFactory, inboundConnectionHandler::handle);
		new StoppableThreadConfiguration<ConnectionServer>()
				.setPriority(Settings.threadPrioritySync)
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("connectionServer")
				.setWork(connectionServer)
				.build()
				.start();
		return connectionManagers;
	}

	/**
	 * Constructs and starts all networking components needed for a chatter network to run: readers, writers and a
	 * separate event creation thread.
	 */
	public void startChatterNetwork() {
		final StaticConnectionManagers connectionManagers = startCommonNetwork();

		// first create all instances because of thread safety
		for (final NodeId otherId : topology.getNeighbors()) {
			chatterCore.newPeerInstance(
					otherId.getId(),
					eventTaskCreator::addEvent
			);
		}
		final ParallelExecutor parallelExecutor = new CachedPoolParallelExecutor("chatter");
		for (final NodeId otherId : topology.getNeighbors()) {
			final PeerInstance chatterPeer = chatterCore.getPeerInstance(otherId.getId());
			final ShadowGraphSynchronizer chatterSynchronizer = new ShadowGraphSynchronizer(
					getShadowGraph(),
					getNumMembers(),
					getStats(),
					consensusRef::get,
					sr -> {
					},
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
					}
			);
			new StoppableThreadConfiguration<>()
					.setPriority(Thread.NORM_PRIORITY)
					.setNodeId(selfId.getId())
					.setComponent(PLATFORM_THREAD_POOL_NAME)
					.setOtherNodeId(otherId.getId())
					.setThreadName("ChatterReader")
					.setWork(new NegotiatorThread(
							connectionManagers.getManager(otherId, topology.shouldConnectTo(otherId)),
							List.of(
									new VersionCompareHandshake(
											appMain.getSoftwareVersion(),
											!Settings.gossipWithDifferentVersions),
									new VersionCompareHandshake(
											PlatformVersion.locateOrDefault(),
											!Settings.gossipWithDifferentVersions)
							),
							new NegotiationProtocols(List.of(
									new ReconnectProtocol(
											otherId,
											reconnectThrottle,
											() -> signedStateManager.getLastCompleteSignedState().get(),
											Settings.reconnect.getAsyncStreamTimeoutMilliseconds(),
											stats.getReconnectStats(),
											reconnectController.get()
									),
									new ChatterSyncProtocol(
											chatterPeer.communicationState(),
											chatterPeer.outputAggregator(),
											chatterSynchronizer),
									new ChatterProtocol(chatterPeer, parallelExecutor)
							))
					))
					.build()
					.start();
		}
		final OtherParentTracker otherParentTracker = new OtherParentTracker();
		final EventCreationRules eventCreationRules = LoggingEventCreationRules.create(
				List.of(
						startUpEventFrozenManager,
						freezeManager,
						fallenBehindManager,
						new ChatteringRule(
								Settings.chatter.getChatteringCreationThreshold(),
								chatterCore.getPeerInstances().stream().map(PeerInstance::communicationState).toList()
						),
						swirldStateManager.getTransactionPool(),
						new BelowIntCreationRule(intakeQueue::size, Settings.chatter.getChatterIntakeThrottle())
				),
				List.of(
						StaticCreationRules::nullOtherParent,
						otherParentTracker,
						new AncientParentsRule(consensusRef::get),
						criticalQuorum)
		);
		final ChatterEventCreator chatterEventCreator = new ChatterEventCreator(
				this,
				selfId,
				PlatformConstructor.platformSigner(crypto.getKeysAndCerts()),
				swirldStateManager.getTransactionPool(),
				CommonUtils.combineConsumers(
						eventTaskCreator::createdEvent,
						otherParentTracker::track,
						chatterEventMapper::mapEvent
				),
				chatterEventMapper::getMostRecentEvent,
				eventCreationRules);

		if (startingFromGenesis()) {
			// if we are starting from genesis, we will create a genesis event, which is the only event that will
			// ever be created without an other-parent
			chatterEventCreator.createGenesisEvent();
		}
		final EventCreatorThread eventCreatorThread = new EventCreatorThread(
				selfId,
				Settings.chatter.getAttemptedChatterEventPerSecond(),
				initialAddressBook,
				chatterEventCreator::createEvent
		);

		clearAllPipelines = Clearables.of(
				// chatter event creator needs to be cleared first, because it sends event to intake
				eventCreatorThread,
				getIntakeQueue(),
				// eventLinker is not thread safe, so the intake thread needs to be paused while its being cleared
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
	 * Constructs and starts all networking components needed for a sync network to run: heartbeats, callers, listeners
	 */
	public void startSyncNetwork() {
		final StaticConnectionManagers connectionManagers = startCommonNetwork();

		sharedConnectionLocks = new SharedConnectionLocks(
				topology,
				connectionManagers
		);
		final MultiProtocolResponder protocolHandlers = new MultiProtocolResponder(
				List.of(
						ProtocolMapping.map(
								UnidirectionalProtocols.SYNC.getInitialByte(),
								new SyncProtocolResponder(
										simultaneousSyncThrottle,
										shadowgraphSynchronizer,
										syncManager,
										syncManager::shouldAcceptSync,
										stats
								)
						),
						ProtocolMapping.map(
								UnidirectionalProtocols.RECONNECT.getInitialByte(),
								new ReconnectProtocolResponder(
										signedStateManager,
										Settings.reconnect,
										reconnectThrottle,
										stats.getReconnectStats()
								)
						),
						ProtocolMapping.map(
								UnidirectionalProtocols.HEARTBEAT.getInitialByte(),
								HeartbeatProtocolResponder::heartbeatProtocol
						)
				)
		);

		for (final NodeId otherId : topology.getNeighbors()) {
			// create and start new threads to listen for incoming sync requests
			new StoppableThreadConfiguration<Listener>()
					.setPriority(Thread.NORM_PRIORITY)
					.setNodeId(selfId.getId())
					.setComponent(PLATFORM_THREAD_POOL_NAME)
					.setOtherNodeId(otherId.getId())
					.setThreadName("listener")
					.setWork(new Listener(
							protocolHandlers,
							connectionManagers.getManager(otherId, false)
					))
					.build()
					.start();

			// create and start new thread to send heartbeats on the SyncCaller channels
			new StoppableThreadConfiguration<HeartbeatSender>()
					.setPriority(Settings.threadPrioritySync)
					.setNodeId(selfId.getId())
					.setComponent(PLATFORM_THREAD_POOL_NAME)
					.setThreadName("heartbeat")
					.setOtherNodeId(otherId.getId())
					.setWork(new HeartbeatSender(
							otherId,
							sharedConnectionLocks,
							stats,
							PlatformConstructor.settingsProvider())
					)
					.build()
					.start();
		}

		// start the timing AFTER the initial pause
		stats.resetAllSpeedometers();
		// create and start threads to call other members
		for (int i = 0; i < Settings.maxOutgoingSyncs; i++) {
			spawnSyncCaller(i);
		}
	}

	/**
	 * Spawn a thread to initiate syncs with other users
	 */
	private void spawnSyncCaller(final int callerNumber) {
		// create a caller that will run repeatedly to call random members other than selfId
		final SyncCaller syncCaller = new SyncCaller(
				this, getAddressBook(), selfId, callerNumber, reconnectHelper);

		/* the thread that repeatedly initiates syncs with other members */
		final Thread syncCallerThread = new ThreadConfiguration()
				.setPriority(Settings.threadPrioritySync)
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("syncCaller-" + callerNumber)
				.setRunnable(syncCaller)
				.build();

		syncCallerThread.start();
	}

	/**
	 * return the rectangle of the recommended window size and location for this instance of the Platform.
	 * Both consoles and windows are created to fit in this rectangle, by default.
	 *
	 * @return the recommended Rectangle for this Platform's window
	 */
	private Rectangle winRect() {
		// the goal is to arrange windows on the screen so that the leftmost and rightmost windows just
		// touch the edge of the screen with their outermost border. But the rest of the windows overlap
		// with them and with each other such that all of the border of one window (ecept 2 pixels) overlaps
		// the content of its neighbors. This should look fine on any OS where the borders are thin (i.e.,
		// all except Windows 10), and should also look good on Windows 10, by making the invisible borders
		// overlap the adjacent window rather than looking like visible gaps.
		// In addition, extra space is added to either the left or right side of the
		// screen, whichever is likely to have the close button for the Browser window that lies behind the
		// Platform windows.

		final int leftGap = (SystemUtils.IS_OS_WINDOWS ? 0 : 25); // extra space at left screen edge
		final int rightGap = (SystemUtils.IS_OS_WINDOWS ? 50 : 0); // extra space at right screen edge
		final Rectangle screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getMaximumWindowBounds();
		final int winCount = getOwnHostCount(getAddressBook());
		final int contentWidth = (screenSize.width - leftGap - rightGap
				- Browser.insets.left - Browser.insets.right) / winCount;
		final int x = screenSize.x + leftGap + contentWidth * this.winNum;
		final int y = screenSize.y;
		final int width = contentWidth + Browser.insets.left + Browser.insets.right;
		final int height = screenSize.height;
		return new Rectangle(x, y, width, height);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preEvent() {
		if (!Settings.enableStateRecovery) {// recover mode don't allow any new transaction being submitted
			// give the app one last chance to create a non-system transaction and give the platform
			// one last chance to create a system transaction
			appMain.preEvent();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SignedStateFileManager getSignedStateFileManager() {
		return signedStateFileManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FreezeManager getFreezeManager() {
		return freezeManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SyncManagerImpl getSyncManager() {
		return syncManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	ShadowGraph getShadowGraph() {
		return shadowGraph;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SignedStateManager getSignedStateManager() {
		return signedStateManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SharedConnectionLocks getSharedConnectionLocks() {
		return sharedConnectionLocks;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	Crypto getCrypto() {
		return crypto;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventTaskCreator getEventTaskCreator() {
		return eventTaskCreator;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public EventMapper getEventMapper() {
		return eventMapper;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public QueueThread<EventIntakeTask> getIntakeQueue() {
		return intakeQueue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Consensus getConsensus() {
		return consensusRef.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CriticalQuorum getCriticalQuorum() {
		return criticalQuorum;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	TransactionTracker getTransactionTracker() {
		return transactionTracker;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
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
				log.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
						"Platform status changed.",
						oldStatus == null ? "" : oldStatus.name(),
						ns.name()).toString());

				log.info(PLATFORM_STATUS.getMarker(),
						"Platform status changed to: {}",
						newStatus.toString());

				appMain.platformStatusChange(newStatus);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void newConnectionOpened(final Connection sc) {
		activeConnectionNumber.getAndIncrement();
		checkPlatformStatus();
		stats.connectionEstablished(sc);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void connectionClosed(final boolean outbound) {
		final int connectionNumber = activeConnectionNumber.decrementAndGet();
		if (connectionNumber < 0) {
			log.error(EXCEPTION.getMarker(),
					"activeConnectionNumber is {}, this is a bug!",
					connectionNumber);
		}
		checkPlatformStatus();

		if (outbound) {
			getStats().interruptedCallSyncsPerSecond.cycle();
		} else {
			getStats().interruptedRecSyncsPerSecond.cycle();
		}
	}

	/**
	 * Returns the consensus state.
	 */
	@Override
	public SwirldStateManager getSwirldStateManager() {
		return swirldStateManager;
	}

	@Override
	public ConsensusRoundHandler getConsensusHandler() {
		return consensusRoundHandler;
	}

	@Override
	public PreConsensusEventHandler getPreConsensusHandler() {
		return preConsensusEventHandler;
	}

	@Override
	public SwirldMain getAppMain() {
		return appMain;
	}

	/** {@inheritDoc} */
	@Override
	public void addSignedStateListener(final InvalidSignedStateListener listener) {

		signedStateManager.addSignedStateListener(listener);
	}

	/** {@inheritDoc} */
	@Override
	public void appStatInit() {
		appStats.init();
	}

	/** {@inheritDoc} */
	@Override
	public Console createConsole(final boolean visible) {
		if (GraphicsEnvironment.isHeadless()) {
			return null;
		}
		final Rectangle winRect = winRect();
		// remember last 100 lines
		// if SwirldMain calls createConsole, this remembers the window created
		final Console console = new Console(
				getAddressBook().getAddress(selfId.getId()).getSelfName(),
				100, // remember last 100 lines
				winRect, fontSize, false);
		console.getWindow()
				.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

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
			log.error(EXCEPTION.getMarker(), "", e);
		}

		return frame;
	}

	/** {@inheritDoc} */
	@Override
	public Instant estimateTime() {
		// Estimated consensus times are predicted only here and in Event.estimateTime().

		/* seconds from self creating an event to self creating the next event */
		double c2c = 1.0 / Math.max(0.5,
				stats.eventsCreatedPerSecond.get());
		/* seconds from self creating an event to the consensus timestamp that event receives */
		double c2t = stats.getAvgSelfCreatedTimestamp();

		// for now, just use 0. A more sophisticated formula could be used
		c2c = 0;
		c2t = 0;

		return Instant.now().plus((long) ((c2c / 2.0 + c2t) * 1_000_000_000.0),
				ChronoUnit.NANOS);
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
		return consensusRef.get().getAllEvents();
	}

	/** {@inheritDoc} */
	@Override
	public ApplicationStatistics getAppStats() {
		return appStats;
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
	public Statistics getStats() {
		return stats;
	}

	@Override
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
	public byte[] sign(final byte[] data) {
		return crypto.sign(data);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("removal")
	@Override
	public void addAppStatEntry(final Metric newEntry) {
		addAppMetrics(newEntry);
	}

	/** {@inheritDoc} */
	@Override
	public void addAppMetrics(final Metric... metrics) {
		CommonUtils.throwArgNull(metrics, "metrics");
		appStats.addMetrics(metrics);
	}

	/** {@inheritDoc} */
	@Override
	public void recordStatsValue(final StatsType statsType, final double value) {
		if (statsType == StatsType.AVGSTATESIGS) {
			getStats().avgStateSigs.recordValue(value);
		}
	}

	/** {@inheritDoc} */
	@Override
	public AddressBook getAddressBook() {
		return initialAddressBook;
	}

	/**
	 * Get consensusTimestamp of the last signed state
	 *
	 * @return an {@code Instant} which identifies the consensus time stamp of the last signed state
	 */
	public Instant getLastSignedStateTimestamp() {
		return signedStateManager.getLastSignedStateConsensusTimestamp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T extends SwirldState> AutoCloseableWrapper<T> getLastCompleteSwirldState() {
		return signedStateManager.getLastCompleteSwirldState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	EventStreamManager getEventStreamManager() {
		return eventStreamManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	StartUpEventFrozenManager getStartUpEventFrozenManager() {
		return startUpEventFrozenManager;
	}

	/**
	 * Initializes EventStreamManager instance,
	 * which will start threads for calculating RunningHash, and writing event stream files when event
	 * streaming is
	 * enabled
	 *
	 * @param name
	 * 		name of this node
	 */
	void initEventStreamManager(final String name) {
		try {
			log.info(STARTUP.getMarker(), "initialize eventStreamManager");
			eventStreamManager = new EventStreamManager<>(
					this,
					name,
					Settings.enableEventStreaming,
					Settings.eventsLogDir,
					Settings.eventsLogPeriod,
					Settings.eventStreamQueueCapacity,
					this::isLastEventBeforeRestart);
		} catch (final NoSuchAlgorithmException | IOException e) {
			log.error(EXCEPTION.getMarker(), "Fail to initialize eventStreamHelper. Exception: {}",
					ExceptionUtils.getStackTrace(e));
		}
	}

	/**
	 * check whether the given event is the last event in its round, and the platform enters freeze period, or
	 * whether
	 * this event is the last event before shutdown
	 *
	 * @param event
	 * 		a consensus event
	 * @return whether this event is the last event to be added before restart
	 */
	private boolean isLastEventBeforeRestart(final EventImpl event) {
		return (event.isLastInRoundReceived() && swirldStateManager.isInFreezePeriod(
				event.getConsensusTimestamp()))
				|| event.isLastOneBeforeShutdown();
	}

	void setGenesisFreezeTime(final long genesisFreezeTime) {
		this.genesisFreezeTime = genesisFreezeTime;
	}

	public ShadowGraphSynchronizer getShadowGraphSynchronizer() {
		return shadowgraphSynchronizer;
	}

	@Override
	public SimultaneousSyncThrottle getSimultaneousSyncThrottle() {
		return simultaneousSyncThrottle;
	}


	public enum StatsType {
		AVGSTATESIGS
	}
}

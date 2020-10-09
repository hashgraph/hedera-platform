/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform;

import com.swirlds.blob.internal.db.SnapshotManager;
import com.swirlds.blob.internal.db.SnapshotTask;
import com.swirlds.blob.internal.db.SnapshotTaskType;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.Console;
import com.swirlds.common.Constants;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.StatEntry;
import com.swirlds.common.SwirldMain;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.StateLoadedFromDiskNotification;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.threading.StandardThreadFactory;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.platform.SyncCaller.SyncCallerType;
import com.swirlds.platform.event.TransactionConstants;
import com.swirlds.platform.internal.PlatformThreadFactory;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.internal.SystemExitReason;
import com.swirlds.platform.state.SavedStateInfo;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.SignedStateManager;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.swirlds.common.merkle.MerkleUtils.rehashTree;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;
import static com.swirlds.platform.Browser.exitSystem;
import static com.swirlds.platform.internal.SystemExitReason.SAVED_STATE_NOT_LOADED;
import static com.swirlds.platform.internal.SystemExitReason.SWIRLD_MAIN_THREW_EXCEPTION;

public class SwirldsPlatform extends AbstractPlatform {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** the name of the swirld being run */
	private final String swirldName;
	// The following hold info about the swirld being run (appMain), and the platform's id
	/** the name of the main class this platform will be running */
	private final String mainClassName;
	/** the main program for the app. (The app also has a SwirldState, managed by eventFlow) */
	private SwirldMain appMain;

	/** this is the Nth Platform running on this machine (N=winNum) */
	private final int winNum;
	/** parameters given to the app when it starts */
	private final String[] parameters;
	/** the connection ID for self (must be from 0 to N-1 for N members, currently equal to selfId) */
	private final NodeId selfConnectionId;
	/** set in the constructor and given to the SwirldState object in run() */
	private AddressBook initialAddressBook = null;
	/** number of lines of text to show in the text window */
	private final int numLines;

	/** used to calculate Java hashes for HashMap keys that are cryptographic hashes */
	private final int hashMapSeed;

	/** Database Statistics */
	private final DBStatistics dbStats;

	/** various signed states in various stages of collecting signatures */
	private final SignedStateManager signedStateManager;

	/** a long name including (app, swirld, member id, member self name) */
	private String platformName;

	/** this Platform's infoMember, representing the (app, swirld ID, member ID) triplet it is running */
	private StateHierarchy.InfoMember infoMember;


	/** The platforms freeze manager */
	private final FreezeManager freezeManager;

	/** the initial signed state that was loaded from disk on platform startup */
	private volatile SignedState signedState = null;
	/** the initial swirld state that was loaded from disk on platform startup, this gets passed to eventFlow */
	private volatile SwirldState swirldState = null;

	/** tell which pairs of members should establish connections (syncing randomly within that set) */
	RandomGraph connectionGraph;

	/**
	 * The shadow graph manager. This wraps a shadow graph, which is an Event graph that
	 * adds child pointers to the Hashgraph Event graph. Used for gossiping.
	 */
	private SyncShadowGraphManager sgm;

	/** tells callers who to sync with and keeps track of whether we have fallen behind */
	private SyncManager syncManager;

	/**
	 * a single thread uses this to listen for incoming requests to set up a connection for future syncing
	 */
	private SyncServer syncServer;
	/**
	 * the SyncClient that holds all the SyncConnection objects, one for each member it called to create a
	 * connection with.
	 */
	private SyncClient syncClient;

	/** the thread in which this platform's SwirldMain app is run */
	private Thread mainThread;
	/** the thread that listens for incoming requests from any member to establish new connections */
	private Thread serverThread;
	/** the thread that repeatedly initiates syncs with other members */
	private Thread syncCallerThread;

	/** a Timer with one thread that calls stats.updateOthers() once a second */
	private Timer statTimer;
	/** a Timer with one thread that calls Utilities.printDeadlocks() once a second */
	@SuppressWarnings("unused") // keep a reference to the thread, for future debugging etc
	private Timer deadlockDetectorTimer;

	/** a thread that writes, reads and deletes FastCopyable object to/from files */
	private SignedStateFileManager signedStateFileManager;

	/**
	 * contains a thread that calculates RunningHash for consensus events,
	 * calls {@link EventImpl#setRunningHash(Hash)} to set RunningHash for the consensus events,
	 * sends consensus events to forCons queue and StreamObjectConsumer for event streaming
	 */
	private RunningHashCalculator runningHashCalculator;

	/**
	 * an array containing the last sync speed for each of the nodes in bytes/second. used by PRIORITY
	 * SyncCaller
	 */
	private AtomicReferenceArray<Double> lastSyncSpeed;

	/** last time stamp when pause check timer is active */
	private long pauseCheckTimeStamp;
	/** alert threshold for java app pause */
	private static final long PAUSE_ALERT_INTERVAL = 5000;

	/**
	 * All the SyncListener threads, which are each listening for incoming sync requests from a single
	 * member
	 */
	private final List<Thread> syncListenerThreads = new LinkedList<>();

	/** The last status of the platform that was determined, is null until the platform starts up */
	private final AtomicReference<PlatformStatus> currentPlatformStatus = new AtomicReference<PlatformStatus>(null);
	/** the round number of last recover state with valid new user transactions */
	private long roundOfLastRecoveredEvent;
	/** the number of active connections this node has to other nodes */
	private final AtomicInteger activeConnectionNumber = new AtomicInteger(0);

	/**
	 * the ID of the member running this. Since a node can be a main node or a mirror node, the ID is not a primitive
	 * value
	 */
	protected final NodeId selfId;

	/** if SwirldMain calls createConsole, this remembers the window created */
	private Console console = null;

	/** the app can set appAbout, which shows in the menu's "about" */
	private String appAbout = "";

	/** all the events and other data about the hashgraph */
	protected final Hashgraph hashgraph;

	/** threads and queues of events to record, with/without consensus */
	protected EventFlow eventFlow;

	/** font size to use in the console text window */
	protected int fontSize;

	/** statistics to monitor the network, syncing, etc. */
	protected Statistics stats;

	/** Application statistics */
	protected ApplicationStatistics appStats;

	/** sleep in ms after each sync in SyncCaller. A public setter for this exists. */
	private long delayAfterSync = 0;

	/** ID number of the swirld being run */
	protected byte[] swirldId;

	/** the object that contains all key pairs and CSPRNG state for this member */
	protected Crypto crypto;

	public enum StatsType {
		AVGSTATESIGS
	}

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
	 * @param numLines
	 * 		a recommended number of lines per window
	 * @param mainClassName
	 * 		the name of the app class inheriting from SwirldMain
	 * @param swirldName
	 * 		the name of the swirld being run
	 * @param appLoader
	 * 		the object used to load the user app
	 */
	SwirldsPlatform(int winNum, String[] parameters, Crypto crypto, byte[] swirldId,
			NodeId id, AddressBook initialAddressBook, int fontSize, int numLines,
			String mainClassName, String swirldName, SwirldAppLoader appLoader) {
		// ThreadDumpGenerator.createDeadlock(); // intentionally deadlock, for debugging
		this.mainClassName = mainClassName;
		this.swirldName = swirldName;
		try {
			this.appMain = appLoader.instantiateSwirldMain();
		} catch (Exception e) {
			CommonUtils.tellUserConsolePopup("ERROR", "ERROR: There are problems starting class " +
					mainClassName + "\n" + ExceptionUtils.getStackTrace(e));
			log.error(EXCEPTION.getMarker(), "Problems with class {}", mainClassName, e);
		}

		this.winNum = winNum;
		this.parameters = parameters;
		// the memberId of the member running this Platform object
		this.selfId = id;
		// this member's ID for connection purposes (0 to N-1). in the current software, equals selfId.
		this.selfConnectionId = id;
		// set here, then given to the state in run(). A copy of it is given to hashgraph.
		this.initialAddressBook = initialAddressBook;
		// hashgraph and state get separate copies of the address book
		this.hashgraph = new Hashgraph(this, initialAddressBook.copy(), selfId, true,
				Executors.newFixedThreadPool(
						Math.max(1, Runtime.getRuntime().availableProcessors() * Settings.eventIntakeThreadMultiplier),
						new PlatformThreadFactory("event_intake_")));

		this.sgm = new SyncShadowGraphManager(this.hashgraph);

		this.fontSize = fontSize;
		this.numLines = numLines;

		this.stats = new Statistics(this);

		this.eventFlow = null;
		// hashMapSeed avoids attacks of triggering many HashMap collisions
		this.hashMapSeed = (new Random()).nextInt() ^ (id.getIdAsInt() * 10_000);
		this.swirldId = swirldId.clone();
		this.crypto = crypto;
		this.appStats = new ApplicationStatistics();
		if (displayDBStats()) {
			this.dbStats = new DBStatistics();
		} else {
			this.dbStats = null;
		}

		if (Settings.state.getSaveStatePeriod() > 0 || Settings.state.dumpStateOnISS || Settings.jsonExport.isActive()) {
			signedStateFileManager = new SignedStateFileManager(this);
			StandardThreadFactory.newThread("objectFileManager", signedStateFileManager,
					selfId, Settings.threadPriorityNonSync
			).start();
		}
		signedStateManager = new SignedStateManager(this, crypto, signedStateFileManager, Settings.state,
				Settings.jsonExport);

		freezeManager = new FreezeManager(this, selfId, this::getStats, this::checkPlatformStatus);

		runningHashCalculator = new RunningHashCalculator(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void setInfoMember(StateHierarchy.InfoMember infoMember) {
		this.infoMember = infoMember;
		this.platformName = infoMember.name//
				+ " - " + infoMember.swirld.name //
				+ " - " + infoMember.swirld.app.name;
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
	DBStatistics getDBStatistics() {
		return dbStats;
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
	String getMainClassName() {
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
	public boolean createSystemTransaction(byte[] trans) {
		return eventFlow.createTransaction(true, new Transaction(trans, true));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	synchronized boolean loadSavedStateFromDisk() throws SignedStateLoadingException {

		SavedStateInfo[] savedStateFiles = SignedStateFileManager.getSavedStateFiles(mainClassName,
				selfId, swirldName);
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
					final SwirldState emptySwirldState = appMain.newState();
					final AddressBook addressBookCopy = initialAddressBook.copy();

					// hashgraph and state get separate copies of addressBook
					emptySwirldState.init(this, addressBookCopy);

					final SignedState legacySignedState = new SignedState(emptySwirldState);
					final Pair<Hash, SignedState> signedStatePair = SignedStateFileManager.readSavedState(
							savedStateFiles[i], legacySignedState);

					final Hash oldHash = signedStatePair.getKey();
					signedState = signedStatePair.getValue();


					// When loading from disk, we should hash the state every time so that the first fast copy will
					// only hash the difference
					final Hash newHash = rehashTree(signedState);

					if (Settings.checkSignedStateFromDisk) {
						if (newHash.equals(oldHash)) {
							log.info(STARTUP.getMarker(), "Signed state loaded from disk has a valid hash.");
						} else {
							log.error(STARTUP.getMarker(),
									"ERROR: Signed state loaded from disk has an invalid hash!\ndisk:{}\ncalc:{}",
									oldHash, newHash);
							//throw new Exception("Signed state loaded from disk has an invalid hash!");
						}
					}

					swirldState = signedState.getState().copy();

					if (signedState != legacySignedState) {
						// The signed state used to be deserialized with copyFrom on a state that had init called on it
						// beforehand. With merkle serialization, a state is created by the util with a no-args
						// constructor. So we need to call init() after it is deserialized since it was never called on
						// this state
						swirldState.init(this, addressBookCopy);
					}

					signedStateManager.addCompleteSignedState(signedState, true);

					// we currently do not use the saved address book
					// initialAddressBook = signedState.getAddressBook();
					hashgraph.loadFromSignedState(signedState);

					loadedSavedState = true;
				} catch (Exception e) {
					signedState = null;
					throw new SignedStateLoadingException("Exception while reading signed state!", e);
				}

				if (!restorePostgresBackup(signedState)) {
					log.error(STARTUP.getMarker(),
							"ERROR: Failed to restore postgres sql snapshot: {}",
							savedStateFiles[i].getStateFile().getAbsolutePath());
					throw new SignedStateLoadingException(String.format("Failed to restore postgres sql snapshot: %s",
							savedStateFiles[i].getStateFile().getAbsoluteFile()));
				}
			} else {
				// delete the older ones
				SignedStateFileManager.deleteRecursively(
						savedStateFiles[i].getDir());// delete the dir it is in
			}
		}
		return loadedSavedState;
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

		Instant loadedLastTimestamp = signedState.getConsensusTimestamp();
		log.info(EVENT_PARSER.getMarker(), "Last timestamp from loaded state is {} of round {}",
				loadedLastTimestamp,
				signedState.getLastRoundReceived());
		Instant endTimeStamp = Instant.MAX;
		if (!Settings.playbackEndTimeStamp.isEmpty()) {
			try {
				endTimeStamp = Instant.parse(Settings.playbackEndTimeStamp);
			} catch (DateTimeParseException e) {
				log.info(EXCEPTION.getMarker(), "Parsing playbackEndTimeStamp error ", e);
			}
		}

		// the round number of last signed state loaded from disk
		long roundOfLoadedSignedState = signedState.getLastRoundReceived();

		//run parser on a different thread, so platform can poll and insert to forCons at the same time
		// to avoid memory failure if there were huge amount of events to be loaded
		String name = "";
		if (this.getAddress().getMemo() != null && !this.getAddress().getMemo().isEmpty()) {
			name = this.getAddress().getMemo();
		} else {
			name = String.valueOf(selfId);
		}

		StreamEventParser streamEventParser = new StreamEventParser(
				Settings.playbackStreamFileDirectory + "/events_" + name,
				loadedLastTimestamp,
				endTimeStamp,
				roundOfLoadedSignedState,
				this.selfId);
		streamEventParser.start();

		roundOfLastRecoveredEvent = Long.MAX_VALUE;

		NotificationFactory.getEngine().register(StateWriteToDiskCompleteListener.class, (notification) -> {
			// in recover mode, once we saved the last recovered signed state we can exit recover mode
			if (notification.getRoundNumber() >= roundOfLastRecoveredEvent) {
				log.info(EVENT_PARSER.getMarker(),
						"Last recovered signed state has been saved in state recover mode. round: {}",
						() -> notification.getRoundNumber());
				// sleep 10 secs to let event stream finish writing the last file
				try {
					Thread.sleep(10 * Constants.SEC_TO_MS);
				} catch (InterruptedException e) {
					log.error(EXCEPTION.getMarker(), "could not sleep", e);
				}
				exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
			}
		});

		long forConsQueueCount = 0;
		long addForConsEventsCount = 0;
		long minGen = Long.MAX_VALUE;
		EventImpl event;
		EventImpl prevRecoveredEvent = null;
		long lastRecoverRoundWithNewUserTran = roundOfLoadedSignedState;
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
						hashgraph.setLastRRwithUserTransaction(event.getRoundReceived());
					}

					//manually update minGenInfo
					eventFlow.addMinGenInfo(event.getRoundReceived(), minGen);
					minGen = Long.MAX_VALUE;
				}

				// whenever we polled an event from parser, we don't know whether it will be the last
				// recovered event, so we save it to placeholder, and push the previous recovered event
				// to forCons queue
				if (prevRecoveredEvent != null) {
					runningHashCalculator.forRunningHashPut(prevRecoveredEvent);
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
				hashgraph.setLastRRwithUserTransaction(lastRecoverRoundWithNewUserTran);
				//manually update minGenInfo
				eventFlow.addMinGenInfo(prevRecoveredEvent.getRoundReceived(), minGen);
			}
			prevRecoveredEvent.setLastOneBeforeShutdown(true);
			runningHashCalculator.forRunningHashPut(prevRecoveredEvent);
			forConsQueueCount++;
			// only update roundOfLastRecoveredEvent when all events have been recovered from even files
			roundOfLastRecoveredEvent = prevRecoveredEvent.getRoundReceived();
		}

		if (addForConsEventsCount != streamEventParser.getEventsCounter()) {
			log.error(EXCEPTION.getMarker(),
					"Error : Number of events recovered not equal to added to forCons {}",
					addForConsEventsCount);
		}

		log.info(EVENT_PARSER.getMarker(), "Inserted {} event to forCons queue", addForConsEventsCount);
		log.info(EVENT_PARSER.getMarker(), "forConsQueueCount {}", forConsQueueCount);
		log.info(EVENT_PARSER.getMarker(), "lastRecoverRoundWithNewUserTran {}", lastRecoverRoundWithNewUserTran);
		log.info(EVENT_PARSER.getMarker(), "roundOfLastRecoveredEvent {}", roundOfLastRecoveredEvent);

		if (addForConsEventsCount == 0) {
			log.info(EVENT_PARSER.getMarker(), "No event recovered from event files");
			exitSystem(SystemExitReason.STATE_RECOVER_FINISHED);
		}
	}

	/**
	 * Restores the postgres backup
	 *
	 * @param signedState
	 * 		the complete signed state
	 * @return true if it will be restored or Settings.dbRestore.isActive() is false, false otherwise
	 */
	boolean restorePostgresBackup(final SignedState signedState) {
		if (Settings.dbRestore.isActive()) {
			SnapshotTask snapshotTask = new SnapshotTask(
					SnapshotTaskType.RESTORE,
					getMainClassName(),
					getSwirldName(),
					getSelfId(),
					signedState.getLastRoundReceived());

			SnapshotManager.restoreSnapshot(snapshotTask);

			snapshotTask.waitFor();

			return !snapshotTask.isError();
		}

		return true;
	}

	/**
	 * First part of initialization. This was split up so that appMain.init() could be called before {@link
	 * StateLoadedFromDiskNotification} would be dispatched. Eventually, this should be split into more discrete parts.
	 */
	void initializeFirstStep() {
		// if this setting is 0 or less, there is no startup freeze
		if (Settings.freezeSecondsAfterStartup > 0) {
			freezeManager.setStartupFreezeEnd(Instant.now()
					.plusSeconds(Settings.freezeSecondsAfterStartup));
		}

		connectionGraph = new RandomGraph(initialAddressBook.getSize(),
				Settings.numConnections, 0);

		if (signedState != null) {
			log.debug(STARTUP.getMarker(),
					"Platform {} has loaded a saved state for round {}, consensusTimetamp {}. Will freeze until: {}",
					selfId, signedState.getLastRoundReceived(),
					signedState.getConsensusTimestamp(),
					signedState.getConsensusTimestamp(),
					freezeManager.getStartupFreezeEnd() == null ? "[won't freeze]"
							: LocalDateTime.ofInstant(freezeManager.getStartupFreezeEnd(),
							ZoneId.systemDefault()).toString());
			// eventFlow will get a copy of the state loaded, that copy will become stateCons. The original state will
			// be saved in the SignedStateMgr and will be deleted when it becomes old
			eventFlow = new EventFlow(this, swirldState);
			eventFlow.loadDataFromSignedState(signedState, false);
		} else {
			SwirldState state = appMain.newState();
			state.genesisInit(this, initialAddressBook.copy()); // hashgraph and state get separate copies of
			// addressBook
			// this state passed will become stateCons
			eventFlow = new EventFlow(this, state);
			// if we are not starting from a saved state, don't freeze on startup
			freezeManager.setStartupFreezeEnd(null);
		}
		eventFlow.startAll(); // start all threads managing the queues.

		try {
			appMain.init(this, selfId);
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "Exception while calling {}.init! Exiting...", mainClassName, e);
			Browser.exitSystem(SWIRLD_MAIN_THREW_EXCEPTION);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void run() {
		if (Settings.enableEventStreaming && Settings.enableStateRecovery) {
			// in state recover mode, we should start writing event stream files at the first complete window,
			// otherwise the first event stream file generated might not match original event stream file
			runningHashCalculator.setStartWriteAtCompleteWindow(true);
		}
		// start thread for calculating RunningHash of consensus events;
		Address address = hashgraph.getAddressBook().getAddress(selfId.getId());

		if (Settings.enableEventStreaming) {
			String eventStreamDir = Settings.eventsLogDir + "/events_";
			if (address.getMemo() != null && !address.getMemo().isEmpty()) {
				eventStreamDir += address.getMemo();
			} else {
				eventStreamDir += selfId;
			}
			runningHashCalculator.setEventStreamDir(eventStreamDir);
		}

		// start thread for calculating RunningHash
		runningHashCalculator.startCalcRunningHashThread();

		syncManager = new SyncManager(
				hashgraph,
				eventFlow,
				connectionGraph,
				selfId,
				() -> freezeManager.isEventCreationFrozen(),
				() -> syncServer == null ? 0 : syncServer.numListenerSyncs.get(),
				() -> signedStateManager.getLastRoundSavedToDisk(),
				() -> signedStateManager.getLastCompleteRound(),
				() -> checkPlatformStatus());

		// a genesis event could be created here, but it isn't needed. This member will naturally create an
		// event after their first sync, where the first sync will involve sending no events.

		if (Settings.statUpdatePeriod > 0) {// if -1, then never update, don't create the thread
			statTimer = new Timer("stat timer", true);
			statTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					stats.updateOthers();
					appStats.updateOthers();
					if (displayDBStats()) {
						dbStats.updateOthers();
					}
				}
			}, 0, Settings.statUpdatePeriod); // update statistics periodically, starting now
		}

		mainThread = StandardThreadFactory.newThread("appMainRun", appMain,
				selfId, Settings.threadPriorityNonSync);

		//In recover mode, skip sync with other nodes,
		// and don't start main to accept any new transactions
		if (!Settings.enableStateRecovery) {
			mainThread.start();

			// allow other members to create connections to me
			spawnSyncServer();

			// create a client that can create new connections to the server
			syncClient = new SyncClient(this);

			// create and start new threads to listen for syncs
			for (int i = 0; i < hashgraph.getAddressBook().getSize(); i++) {
				if (connectionGraph.isAdjacent(selfId.getIdAsInt(), i)) {
					// create and start new thread to listen for incoming sync requests
					spawnSyncListener(NodeId.createMain(i));

					// create and start new thread to send heartbeats on the SyncCaller channels
					SyncHeartbeat sh = new SyncHeartbeat(this, NodeId.createMain(i));
					Thread t = StandardThreadFactory.newThread("heartbeat", sh, selfId, NodeId.createMain(i),
							Settings.threadPrioritySync
					);
					t.start();
				}
			}
			lastSyncSpeed = new AtomicReferenceArray<>(
					hashgraph.getAddressBook().getSize());
			// create and start threads to call other members
			for (int i = 0; i < Settings.maxOutgoingSyncs; i++) {
				if (i < Settings.maxOutgoingRandomSyncs) {
					spawnSyncCaller(i, SyncCallerType.RANDOM);
				} else {
					spawnSyncCaller(i, SyncCallerType.PRIORITY);
				}
			}
		}

		if (Settings.csvFileName != null && Settings.csvFileName.trim().length() > 0) {
			StandardThreadFactory.newThread("CsvWriter", new CsvWriter(this, selfId.toString(), Settings.csvFileName),
					selfId, Settings.threadPriorityNonSync).start();
		}

		if (Settings.runPauseCheckTimer) {
			// periodically check current time stamp to detect whether the java application
			// has been paused for a long period
			Timer pauseCheckTimer = new Timer("pause check", true);
			pauseCheckTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					long currentTimeStamp = System.currentTimeMillis();
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
		if (signedState != null) {
			signedState = null;// we won't need this after this point
			swirldState = null;// we won't need this after this point
		}

		// When the SwirldMain quits, end the run() for this platform instance
		try {
			mainThread.join();
		} catch (InterruptedException e) {
		}
	}

	// spawn a thread to handle incoming TCP/IP connections
	private void spawnSyncServer() {
		Address address = hashgraph.getAddressBook().getAddress(selfId.getId());
		syncServer = new SyncServer(this, address.getListenAddressIpv4(),
				address.getListenPortIpv4());
		serverThread = StandardThreadFactory.newThread("syncServer", syncServer,
				selfId, Settings.threadPrioritySync);
		serverThread.start();
	}

	// spawn a thread to handle incoming syncs from user otherId
	private void spawnSyncListener(NodeId otherId) {
		SyncListener syncListener = new SyncListener(this,
				hashgraph.getAddressBook(), selfId, otherId);
		Thread syncListenerThread = StandardThreadFactory.newThread("syncListener", syncListener,
				selfId, otherId, Settings.threadPrioritySync);
		syncListenerThread.start();
		syncListenerThreads.add(syncListenerThread);
	}

	/**
	 * Spawn a thread to initiate syncs with other users
	 *
	 * @param callerNumber
	 * 		0 for the first caller thread created by this platform, 1 for the next, etc
	 */
	private void spawnSyncCaller(int callerNumber, SyncCaller.SyncCallerType callerType) {
		// create a caller that will run repeatedly to call random members other than selfId
		SyncCaller syncCaller = new SyncCaller(this, hashgraph.getAddressBook(),
				selfId, callerNumber, callerType);
		syncCallerThread = StandardThreadFactory.newThread("syncCaller-" + callerNumber, syncCaller,
				selfId, Settings.threadPrioritySync
		);
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

		int leftGap = (SystemUtils.IS_OS_WINDOWS ? 0 : 25); // extra space at left screen edge
		int rightGap = (SystemUtils.IS_OS_WINDOWS ? 50 : 0); // extra space at right screen edge
		Rectangle screenSize = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getMaximumWindowBounds();
		int winCount = hashgraph.getAddressBook().getOwnHostCount();
		int contentWidth = (screenSize.width - leftGap - rightGap
				- Browser.insets.left - Browser.insets.right) / winCount;
		int x = screenSize.x + leftGap + contentWidth * this.winNum;
		int y = screenSize.y;
		int width = contentWidth + Browser.insets.left + Browser.insets.right;
		int height = screenSize.height;
		return new Rectangle(x, y, width, height);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void preEvent() {
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
	boolean displayDBStats() {
		return (Settings.showDBStats && Settings.showInternalStats);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void handleSystemTransaction(long creator, boolean isConsensus,
			Instant timeCreated, Instant timestamp, Transaction trans,
			Address address) {
		try {
			switch (trans.getContents(0)) {
				case TransactionConstants.SYS_TRANS_STATE_SIG: // a signature on a signed state
				case TransactionConstants.SYS_TRANS_STATE_SIG_FREEZE: // the same thing on the receiving side
					// self-signature was recorded when it was created, so only record other-sigs here
					if (!selfId.equalsMain(creator)) {
						// format of this system transaction:
						// trans[0] is Event.SYS_TRANS_STATE_SIG
						// trans[1...8] is the last round received
						// trans[9...] is the signature
						int pos = 1, len = 8;
						long lastRoundReceived = Utilities
								.toLong(trans.getContentsDirect(), pos);
						pos += len;
						byte[] sig = Arrays.copyOfRange(
								trans.getContentsDirect(), pos,
								trans.getLength());
						log.debug(STATE_SIG_DIST.getMarker(),
								"platform {} got sig from {} for round {} at handleSystemTransaction",
								selfId, creator, lastRoundReceived);
						signedStateManager.recordStateSig(lastRoundReceived,
								creator, null, sig);
					}
					break;
				default:
					log.error(EXCEPTION.getMarker(),
							"Unknown system transaction type {}",
							trans.getContents(0));
					break;
			}
		} catch (Throwable e) {
			log.error(EXCEPTION.getMarker(),
					"Error while handling transaction: kind {} id {} isConsensus {} transaction {} error",
					(trans == null || trans.getLength() == 0) ? 0
							: trans.getContents(0),
					creator, isConsensus, trans, e);

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
	SyncManager getSyncManager() {
		return syncManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SyncShadowGraphManager getSyncShadowGraphManager() {
		return sgm;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	NodeId getSelfConnectionId() {
		return selfConnectionId;
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
	SyncClient getSyncClient() {
		return syncClient;
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
	RandomGraph getConnectionGraph() {
		return connectionGraph;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Hashgraph getHashgraph() {
		return hashgraph;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	SyncServer getSyncServer() {
		return syncServer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	void checkPlatformStatus() {
		int numNodes = initialAddressBook.getSize();

		synchronized (currentPlatformStatus) {
			PlatformStatus newStatus = null;
			if (numNodes > 1 && activeConnectionNumber.get() == 0) {
				newStatus = PlatformStatus.DISCONNECTED;
			} else if (getSyncManager().hasFallenBehind()) {
				newStatus = PlatformStatus.BEHIND;
			} else if (Settings.waitAtStartup && getHashgraph().getNumNotStarted() > 0) {
				newStatus = PlatformStatus.STARTING_UP;
			} else if (freezeManager.shouldEnterMaintenance()) {
				newStatus = PlatformStatus.MAINTENANCE;
			} else {
				newStatus = PlatformStatus.ACTIVE;
			}

			PlatformStatus oldStatus = currentPlatformStatus.getAndSet(newStatus);
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
	 * Notifies the platform that a new connection has been opened
	 */
	@Override
	void newConnectionOpened() {
		activeConnectionNumber.getAndIncrement();
		checkPlatformStatus();
	}

	/**
	 * Notifies the platform that a connection has been closed
	 */
	@Override
	void connectionClosed() {
		int connectionNumber = activeConnectionNumber.decrementAndGet();
		if (connectionNumber < 0) {
			log.error(EXCEPTION.getMarker(),
					"activeConnectionNumber is {}, this is a bug!",
					connectionNumber);
		}
		checkPlatformStatus();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	int getHashMapSeed() {
		return hashMapSeed;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	AbstractEventFlow getEventFlow() {
		return eventFlow;
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
	public Console createConsole(boolean visible) {
		if (GraphicsEnvironment.isHeadless()) {
			return null;
		}
		Rectangle winRect = winRect();
		console = new Console(
				hashgraph.getAddressBook().getAddress(selfId.getId()).getSelfName(),
				100, // remember last 100 lines
				winRect, fontSize, false);
		console.getWindow()
				.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		// console.window.addWindowListener(stopper());
		SwirldMenu.addTo(this, console.getWindow(), 40, Color.white, false);
		console.setVisible(true);
		return console;
	}

	/** {@inheritDoc} */
	@Override
	public boolean createTransaction(Transaction trans) {

		// no new transaction allowed during recover mode
		if (Settings.enableStateRecovery) {
			return false;
		}

		// if the platform is not active, it is better to reject transactions submitted by the app
		if (currentPlatformStatus.get() != PlatformStatus.ACTIVE) {
			return false;
		}
		// create a transaction to be added to the next Event when it is created.
		// The "system" boolean is set to false, because this is an app-generated transaction.
		// For system transactions, the system should call eventFlow.createTransaction directly,
		// rather than calling this public createTransaction method.
		return eventFlow.createTransaction(false, trans);
	}

	/** {@inheritDoc} */
	@Override
	public JFrame createWindow(boolean visible) {
		if (GraphicsEnvironment.isHeadless()) {
			return null;
		}
		Rectangle winRect = winRect();

		JFrame frame = null;
		try {
			Address addr = hashgraph.getAddressBook().getAddress(selfId.getId());
			String name = addr.getSelfName();
			frame = new JFrame(name); // create a new window
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(), "", e);
		}
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setBackground(Color.DARK_GRAY);
		frame.setSize(winRect.width, winRect.height);
		frame.setPreferredSize(new Dimension(winRect.width, winRect.height));
		frame.setLocation(winRect.x, winRect.y);
		SwirldMenu.addTo(this, frame, 40, Color.BLUE, false);
		frame.setFocusable(true);
		frame.requestFocusInWindow();
		frame.setVisible(visible); // show it
		return frame;
	}

	/** {@inheritDoc} */
	@Override
	public Instant estimateTime() {
		// Estimated consensus times are predicted only here and in Event.estimateTime().

		/** seconds from self creating an event to self creating the next event */
		double c2c = 1.0 / Math.max(0.5,
				stats.eventsCreatedPerSecond.getCyclesPerSecond());
		/** seconds from self creating an event to the consensus timestamp that event receives */
		double c2t = stats.avgSelfCreatedTimestamp.getWeightedMean();

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
	public Address getAddress() {
		if (selfId.isMirror()) {
			throw new RuntimeException("Mirror node not yet implemented!");
		}
		return hashgraph.getAddressBook().getAddress(selfId.getId());
	}

	/** {@inheritDoc} */
	@Override
	public Address getAddress(long id) {
		return hashgraph.getAddressBook().getAddress(id);
	}

	/** {@inheritDoc} */
	@Override
	public Event[] getAllEvents() {
		return hashgraph.getAllEvents();
	}

	/** {@inheritDoc} */
	@Override
	public ApplicationStatistics getAppStats() {
		return appStats;
	}

	/** {@inheritDoc} */
	@Override
	public long[] getLastSeqByCreator() {
		return hashgraph.getLastSeqByCreator();
	}

	/** {@inheritDoc} */
	@Override
	public double getLastSyncSpeed(int nodeIndex) {
		Double d = lastSyncSpeed.get(nodeIndex);
		return d == null ? -1 : d;
	}

	/** {@inheritDoc} */
	@Override
	public int getNumMembers() {
		return hashgraph.getAddressBook().getSize();
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
	@SuppressWarnings("unchecked")
	public <T extends SwirldState> T getState() {
		return (T) eventFlow.getCurrStateAndKeep();
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

	/**
	 * Get the transactionMaxBytes in Settings
	 *
	 * @return integer representing the maximum number of bytes allowed in a transaction
	 */
	public static int getTransactionMaxBytes() {
		return Settings.transactionMaxBytes;
	}

	/** {@inheritDoc} */
	@Override
	public void releaseState() {
		eventFlow.releaseStateCurr();
	}

	/** {@inheritDoc} */
	@Override
	public void setAbout(String about) {
		appAbout = about;
	}

	/** {@inheritDoc} */
	@Override
	public void setLastSyncSpeed(int nodeIndex, double lastSyncSpeed) {
		this.lastSyncSpeed.set(nodeIndex, lastSyncSpeed);
	}

	/** {@inheritDoc} */
	@Override
	public void setSleepAfterSync(long delay) {
		delayAfterSync = delay;
	}

	/** {@inheritDoc} */
	@Override
	public byte[] sign(byte[] data) {
		return crypto.sign(data);
	}

	/** {@inheritDoc} */
	@Override
	public void addAppStatEntry(final StatEntry newEntry) {
		appStats.addStatEntry(newEntry);
	}

	/** {@inheritDoc} */
	@Override
	public void setFreezeTime(int startHour, int startMin, int endHour, int endMin) {

		FreezeManager.setFreezeTime(startHour, startMin, endHour, endMin);
	}

	/** {@inheritDoc} */
	@Override
	public void recordStatsValue(StatsType statsType, double value) {
		if (statsType == StatsType.AVGSTATESIGS) {
			getStats().avgStateSigs.recordValue(value);
		}
	}

	/** {@inheritDoc} */
	@Override
	public AddressBook getAddressBook() {
		return hashgraph.getAddressBook();
	}

	/**
	 * Get consensusTimestamp of the last signed state
	 *
	 * @return
	 */
	public Instant getLastSignedStateTimestamp() {
		return signedStateManager.getLastSignedStateConsensusTimestamp();
	}

	@Override
	RunningHashCalculator getRunningHashCalculator() {
		return this.runningHashCalculator;
	}
}

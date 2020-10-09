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
package com.swirlds.platform.state;

import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.SwirldState;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.internal.JsonExporterSettings;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.merkle.hash.MerkleHashChecker;
import com.swirlds.common.threading.StandardThreadFactory;
import com.swirlds.platform.AbstractPlatform;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.FreezeManager;
import com.swirlds.platform.LoggingReentrantLock;
import com.swirlds.platform.ResourceLock;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.TransactionConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.FREEZE;
import static com.swirlds.logging.LogMarker.LAST_COMPLETE_SIGNED_STATE;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.logging.LogMarker.STATE_ON_DISK_QUEUE;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT_NODE;

/**
 * Data structures and methods to manage the various signed states. That includes collecting signatures from
 * the other members, and storing/loading signed states to/from disk.
 */
public class SignedStateManager {
	/**
	 * The latest signed state signed by members with more than 2/3 of total stake.
	 * The state referenced here will hold an archival reservation.
	 *
	 * Must only be set via {@link #setLastCompleteSignedState(SignedState)}.
	 */
	private volatile SignedState lastCompleteSignedState = null;

	/**
	 * The latest signed state signed by self but not by members with more than 2/3 of total stake.
	 * The state reference here will hold a reservation.
	 *
	 * Must only be set via {@link #setLastIncompleteSignedState(SignedState)}.
	 */
	private volatile SignedState lastIncompleteSignedState = null;

	/**
	 * signatures by other members (not self) (not thread safe, because it is private and only accessed in a
	 * synchronized method here)
	 */
	private List<SigInfo> otherSigInfos = new LinkedList<>();

	/**
	 * mapping from round to SignedState for that round. Because it's linked it preserves the order in which
	 * they are added, this is useful for discarding old states.
	 *
	 * Any signed state taken from this data structure MUST be reserved if it is used outside of a synchronized method.
	 */
	private LinkedHashMap<Long, SignedState> allStates = new LinkedHashMap<>();

	/** a list of states that are saved to disk */
	private LinkedList<Long> savedToDisk = new LinkedList<>();
	/** the last SignedState round that is intended to be saved to disk */
	private volatile long lastSSRoundGoingToDisk = -1;

	/** the member ID for self */
	private NodeId selfId;
	/**
	 * The Platform whose address book has all members. Those with more than 2/3 of total stake must sign to
	 * complete
	 */
	private AbstractPlatform platform;
	/** The Crypto object used to verify and sign */
	private Crypto crypto;
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** the timestamp of the last signed state held by the manager */
	private Instant lastSignedStateTimestamp = null;

	/** the timestamp of the last round that reached consensus in the hashgraph */
	private Instant lastHashgraphConsTime = null;

	/** A queue that stores events for a particular round that will be stored in the local state on disk */
	private Queue<Pair<Long, EventImpl[]>> eventsForRound = new ArrayDeque<>(10);

	/** last time the state is saved to disk successfully in millisecond */
	private long lastSaveStateTimeMS;

	/** a thread that provides a new signed state to SwirldMain */
	private Thread newSignedStateThread;

	/** instance in charge of deleting and archiving signed states */
	private final SignedStateGarbageCollector garbageCollector;

	/** a thread that runs the SignedStateGarbageCollector */
	private final Thread garbageCollectorThread;

	/** a thread that writes, reads and deletes FastCopyable object to/from files */
	private SignedStateFileManager signedStateFileManager;

	/** settings that control {@link SignedState} creation, deletion, and disk persistence */
	private StateSettings stateSettings;

	/** settings that control the {@link #jsonifySignedState(SignedState, StateDumpSource)} features and behavior */
	private JsonExporterSettings jsonExporterSettings;

	/** track all registered {@link InvalidSignedStateListener} attached to this {@link SignedStateManager} instance */
	private List<InvalidSignedStateListener> invalidSignedStateListeners;

	/**
	 * A queue that is constantly polled by newSignedStateThread. This queue does not have a capacity like a regular
	 * queue. newSignedStateThread needs to be waiting for an object in order for it to be inserted.
	 *
	 * A state is reserved before it is inserted into this queue.
	 */
	private BlockingQueue<SignedState> newSignedStateQueue = new SynchronousQueue<>();

	/**
	 * the lock providing concurrency control for the {@link #setLastCompleteSignedState(SignedState)} and {@link
	 * #setLastIncompleteSignedState(SignedState)} methods.
	 */
	private LoggingReentrantLock lock;

	/**
	 * the last wall clock time that the {@link #dumpSignedState(SignedState)} method wrote a {@link SignedState} to
	 * disk
	 */
	private double lastISSDumpTimestampSeconds;

	/**
	 * Keeps track of whether a ISS has been logged or not
	 */
	private boolean firstISSLogged = false;

	/**
	 * Used by {@link #jsonifySignedState(SignedState, StateDumpSource)} to control continually dumping signed states
	 * after a reconnect has occurred. This feature is enabled/disabled via the {@link
	 * JsonExporterSettings#isWriteContinuallyEnabled()} setting.
	 */
	private static final AtomicBoolean jsonDumpContinually = new AtomicBoolean(false);

	/**
	 * Mark a SignedState as the most recent completed signed state.
	 */
	private void setLastCompleteSignedState(SignedState ss) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::setLastCompleteSignedState")) {
			if (lastCompleteSignedState != null) {
				lastCompleteSignedState.weakReleaseState();
			}
			if (ss != null) {
				ss.weakReserveState();
				// All signed states from earlier rounds can be archived now.
				for (SignedState state : allStates.values()) {
					if (!state.isMarkedForArchiving() && state.getLastRoundReceived() < ss.getLastRoundReceived()) {
						garbageCollector.archiveBackground(state);
					}
				}
			}
			lastCompleteSignedState = ss;
		}
	}

	/**
	 * Mark a SignedState as the last incomplete signed state.
	 */
	private void setLastIncompleteSignedState(SignedState ss) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::setLastIncompleteSignedState")) {
			if (lastIncompleteSignedState != null) {
				lastIncompleteSignedState.releaseState();
			}
			if (ss != null) {
				ss.reserveState();
			}
			lastIncompleteSignedState = ss;
		}
	}

	/** @return latest round for which we have a supermajority */
	public long getLastCompleteRound() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getLastCompleteRound")) {
			final long result =
					lastCompleteSignedState == null ? -1 : lastCompleteSignedState.getLastRoundReceived();
			return result;
		}
	}

	/**
	 * @return the round number of the latest round saved to disk
	 */
	public long getLastRoundSavedToDisk() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getLastRoundSavedToDisk")) {
			final long result = savedToDisk.size() == 0 ? -1 : savedToDisk.getLast();
			return result;
		}
	}

	/** @return latest round for which we do NOT have a supermajority */
	public long getLastIncompleteRound() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getLastIncompleteRound")) {
			findLastIncompleteSignedState();
			final long result = lastIncompleteSignedState.getLastRoundReceived();
			return result;
		}
	}

	public long getLastSaveStateTimeMS() {
		return lastSaveStateTimeMS;
	}

	/**
	 * Returns which members still haven't signed the latest incomplete signed state (the latest state
	 * signed by self but not signed by members with more than 2/3 of the total stake).
	 *
	 * @return an array where element i is null if the member with ID i has not signed the latest incomplete
	 * 		state
	 */
	public SigInfo[] getNeededLastIncomplete() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getNeededLastIncomplete")) {
			findLastIncompleteSignedState();
			final SigInfo[] result = lastIncompleteSignedState == null ? null :
					lastIncompleteSignedState.getSigSet().getSigInfosCopy();
			return result;
		}
	}

	/**
	 * @return the latest complete signed state, or null if none are complete
	 */
	public AutoCloseableWrapper<SignedState> getLastCompleteSignedState() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getLastCompleteSignedState")) {
			SignedState latest = lastCompleteSignedState;

			Runnable closeCallback = () -> {
				if (latest != null) {
					latest.weakReleaseState();
				}
			};

			if (latest != null) {
				latest.weakReserveState();
			}
			return new AutoCloseableWrapper<>(latest, closeCallback);
		}
	}

	/**
	 * Return the consensus timestamp of the last signed state in the manager, might be null if there are no
	 * states
	 *
	 * @return the consensus timestamp of the last signed state
	 */
	public Instant getLastSignedStateConsensusTimestamp() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getLastSignedStateConsensusTimestamp")) {
			final Instant result = lastSignedStateTimestamp;
			return result;
		}
	}

	/**
	 * set lastIncompleteRound to the last round signed by self but NOT by members with more than 2/3 of the total
	 * stake, or null if none exists
	 */
	private void findLastIncompleteSignedState() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::findLastIncompleteSignedState")) {
			long r = -1;
			SignedState state = null;
			for (SignedState s : allStates.values()) {
				long last = s.getLastRoundReceived();
				if (last > r && !s.getSigSet().isComplete()) {
					r = last;
					state = s;
				}
			}
			setLastIncompleteSignedState(state);
		}
	}

	/** Get the latest signed states. This method creates a copy, so no changes to the array will be made */
	public SignedStateInfo[] getSignedStateInfo() {
		// It is assumed that all data in SignedStateInfo is safe to read even after a state has been
		// archived or deleted. If this is not the case then we need to reserve the states before releasing the info.
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::getSignedStateInfo")) {
			final SignedStateInfo[] result = allStates.values().toArray(new SignedState[0]);
			return result;
		}
	}

	/**
	 * Start empty, with no known signed states. The number of addresses in
	 * platform.hashgraph.getAddressBook() must not change in the future. The addressBook must contain
	 * exactly the set of members who can sign the state. A signed state is considered completed when it has
	 * signatures from members with more than 2/3 of the total stake.
	 *
	 * @param platform
	 * 		the Platform running this, such that platform.hashgraph.getAddressBook() containing
	 * 		exactly those members who can sign
	 */
	public SignedStateManager(final AbstractPlatform platform, final Crypto crypto,
			final SignedStateFileManager signedStateFileManager, final StateSettings stateSettings,
			final JsonExporterSettings jsonExporterSettings) {
		this(platform, crypto, signedStateFileManager, stateSettings,
				jsonExporterSettings, new SignedStateGarbageCollector());
	}

	protected SignedStateManager(final AbstractPlatform platform, final Crypto crypto,
			final SignedStateFileManager signedStateFileManager, final StateSettings stateSettings,
			final JsonExporterSettings jsonExporterSettings,
			final SignedStateGarbageCollector signedStateGarbageCollector) {

		this.selfId = platform.getSelfId();
		this.platform = platform;
		this.crypto = crypto;

		this.signedStateFileManager = signedStateFileManager;
		this.stateSettings = stateSettings;
		this.jsonExporterSettings = jsonExporterSettings;

		// Initialize the list of InvalidStateListeners
		this.invalidSignedStateListeners = new LinkedList<>();

		newSignedStateThread = StandardThreadFactory.newThread(
				"newSignedState", new NewSignedStateRunnable(newSignedStateQueue, platform.getAppMain()),
				selfId, Thread.NORM_PRIORITY, true);
		newSignedStateThread.start();

		this.garbageCollector = signedStateGarbageCollector;//new SignedStateGarbageCollector();

		garbageCollectorThread = StandardThreadFactory.newThread(
				"signedStateGarbageCollector", garbageCollector, selfId, Thread.NORM_PRIORITY, true
		);
		garbageCollectorThread.start();

		lock = LoggingReentrantLock.newLock(this.selfId, false,
				"signedStateMgr-" + selfId.getId());
	}

	/**
	 * Invokes the invalid signed state listeners.
	 *
	 * @param signedState
	 * 		the local signed state instance whose signature failed to match the remote peer
	 * @param sigInfo
	 * 		the signatures received from the remote peer
	 */
	public void notifySignedStateListeners(final SignedState signedState, final SigInfo sigInfo) {
		for (InvalidSignedStateListener listener : invalidSignedStateListeners) {
			if (listener != null) {
				listener.notifyError(platform, signedState.getAddressBook(), signedState.getState(),
						signedState.getEvents(), platform.getSelfId(), new NodeId(true, sigInfo.getMemberId()),
						sigInfo.getRound(), signedState.getConsensusTimestamp(), signedState.getNumEventsCons(),
						signedState.getHashBytes(), signedState.getSwirldStateHash());
			}
		}
	}

	public void addSignedStateListener(final InvalidSignedStateListener listener) {

		if (listener == null) {
			throw new IllegalArgumentException("listener");
		}

		invalidSignedStateListeners.add(listener);
	}

	private boolean shouldSaveToDisk(Instant consensusTimestamp, Instant lastConsTime) {
		// the first round should be saved to disk and every round which is about saveStatePeriod seconds after the
		// previous one should be saved. this will not always be exactly saveStatePeriod seconds after the previous one,
		// but it will be predictable at what time each a state will be saved
		boolean isSavePeriod = lastConsTime == null // the first round should be signed
				|| (stateSettings.getSaveStatePeriod() > 0 &&
				consensusTimestamp.getEpochSecond()
						/ stateSettings.getSaveStatePeriod() > lastConsTime.getEpochSecond()
						/ stateSettings.getSaveStatePeriod());
		// we should save the first state in the freeze period. EventFlow should never send more than one state in the
		// freeze period here
		boolean isInFreeze = FreezeManager.isInFreezePeriod(consensusTimestamp);

		return stateSettings.getSaveStatePeriod() > 0 && // we are saving states to disk
				(isSavePeriod || isInFreeze);
	}

	/**
	 * Keep a new signed state, signed only by self, and start collecting signatures for it.
	 *
	 * @param platform
	 * 		the platform creating this signed state
	 * @param ss
	 * 		the signed state to be kept by the manager
	 */
	public SigSet newSelfSigned(AbstractPlatform platform, SignedState ss) {

		boolean shouldSaveToDisk = shouldSaveToDisk(ss.getConsensusTimestamp(), getLastSignedStateConsensusTimestamp());
		ss.setShouldSaveToDisk(shouldSaveToDisk);

		// Put the round number and signature of the SignedState into a system transaction.
		// There is no need to store the hash, since everyone should agree.
		// There is no need to sign the transaction, because the event will be signed.
		final byte[] sig = crypto.sign(ss.getHashBytes());
		log.info(SIGNED_STATE.getMarker(), "newSelfSigned:: get sig by calling crypto.sign()");
		final byte[] trans = Utilities.concat( // total around 111 to 113 bytes
				ss.isFreezeState() ? TransactionConstants.SYS_TRANS_STATE_SIG_FREEZE
						: TransactionConstants.SYS_TRANS_STATE_SIG, // 1 byte
				ss.getLastRoundReceived(), // 8 bytes
				sig);// around 102 to 104 bytes (but it varies)
		if (ss.getHashBytes().length != Crypto.HASH_SIZE_BYTES) {
			log.error(EXCEPTION.getMarker(),
					"hash.length = {} != {} = Crypto.HASH_SIZE_BYTES",
					ss.getHashBytes().length, Crypto.HASH_SIZE_BYTES);
		}


		boolean success;

		// If beta mirror logic is enabled and this node is zero stake then do not attempt
		// to send the system transaction
		if (SettingsCommon.enableBetaMirror && platform.isZeroStakeNode()) {
			success = true;
		} else {
			success = platform.createSystemTransaction(trans);
		}

		if (!success) {
			log.error(EXCEPTION.getMarker(),
					"failed to create signed state transaction)");
		}

		if (ss.isFreezeState()) {
			log.info(FREEZE.getMarker(), "Hashed state in freeze period, last round is {}",
					ss.getLastRoundReceived());
			if (shouldSaveToDisk) {
				log.info(FREEZE.getMarker(), "Freeze state is about to be saved to disk, round is {}",
						ss.getLastRoundReceived());
			} else {
				if (stateSettings.getSaveStatePeriod() == 0)
					log.info(FREEZE.getMarker(),
							"Freeze WILL NOT be saved to disk since saveStatePeriod is 0 in settings. " +
									"Last state saved to disk is for round {}", lastSSRoundGoingToDisk);
				else
					log.info(FREEZE.getMarker(), "Freeze WILL NOT be saved to disk since there are no new user " +
							"transactions. Last state saved to disk is for round {}", lastSSRoundGoingToDisk);
			}
		}

		if (stateSettings.isEnableStateRecovery()) {
			ss.reserveState();
			// must put in newSignedStateQueue otherwise newSignedState() of main App will not be called
			if (!newSignedStateQueue.offer(ss)) {
				ss.releaseState();
				log.error(SIGNED_STATE.getMarker(),
						"During State Recvoer offer Failed - NewSignedStateQueue [ round = {} ]",
						ss::getLastRoundReceived);
			}
			// save the last recovered state, even state save period requirement is not met
			if (shouldSaveToDisk || ss.getLastRoundReceived() >= platform.getRoundOfLastRecoveredEvent()) {
				ss.weakReserveState();
				signedStateFileManager.saveSignedStateToDisk(ss);
				lastSignedStateTimestamp = ss.getConsensusTimestamp();
			}
			//no need to collect & record signatures in sigSet in recover mode
			return null;
		}

		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::newSelfSigned")) {
			if (shouldSaveToDisk) {
				lastSSRoundGoingToDisk = ss.getLastRoundReceived();

				Pair<Long, EventImpl[]> pair = eventsForRound.poll();
				if (pair == null || pair.getLeft() != ss.getLastRoundReceived()) {
					log.error(EXCEPTION.getMarker(),
							"Cannot find events for local storage for round {}. Local data will be empty.",
							ss.getLastRoundReceived());
				} else {
					LocalStateEvents localStateEvents = new LocalStateEvents();
					localStateEvents.setEvents(pair.getRight());
					ss.setLocalStateEvents(localStateEvents);
				}
			}
			// lastRoundFameDecided is the roundReceived for the last Event for which there is
			// consensus. So this is the round that the signed state represents, and is used as the key in the Map
			// allStates.
			allStates.put(ss.getLastRoundReceived(), ss);
			lastSignedStateTimestamp = ss.getConsensusTimestamp();

			SigSet sigSet = recordStateSig(ss.getLastRoundReceived(), platform.getSelfId().getId(), ss.getHashBytes(),
					sig);
			return sigSet;
		}
	}

	/**
	 * Notifies the signed state manager that consensus has been reached on a particular round. The manager will store
	 * events in local memory if they later need to be stored to disk.
	 *
	 * @param round
	 * 		the round consensus has been reached on
	 * @param timestamp
	 * 		the timestamp of the round
	 * @param eventsGetter
	 * 		a getter for all events in memory
	 */
	public void consensusReachedOnRound(long round, Instant timestamp, Supplier<EventImpl[]> eventsGetter) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::consensusReachedOnRound")) {
			if (shouldSaveToDisk(timestamp, lastHashgraphConsTime)) {
				lastHashgraphConsTime = timestamp;
				Pair<Long, EventImpl[]> pair = Pair.of(round, eventsGetter.get());
				if (!eventsForRound.offer(pair)) {
					log.error(EXCEPTION.getMarker(),
							"eventsForRound queue is full! Events for round {} will not be stored to the local state",
							round);
				}
			}
		}
	}

	/**
	 * Record a signature for the signed state for the given round with the given hash. The caller must not
	 * change the array elements after passing this in. Each time the caller calls this method with
	 * memberId==selfId, the lastRoundReceived parameter must be greater than on the previous such call.
	 *
	 * @param lastRoundReceived
	 * 		the signed state reflects all events with received round less than or equal to this
	 * @param memberId
	 * 		the member ID of the signer
	 * @param hash
	 * 		the hash of the state to be signed. The signature algorithm may internally hash this
	 * 		hash.
	 * @param sig
	 * 		the signature
	 * @return the SigSet that the new SigInfo is added to
	 */
	public SigSet recordStateSig(long lastRoundReceived, long memberId,
			byte[] hash, byte[] sig) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::recordStateSig")) {
			SigInfo sigInfo = new SigInfo(lastRoundReceived, memberId, hash, sig);
			SignedState signedState = allStates.get(lastRoundReceived);
			SigSet sigSet = signedState == null ? null : signedState.getSigSet();
			if (selfId.equalsMain(memberId)) {
				if (sigSet == null) {
					// this should never happen, unless there is a malicious node
					log.error(TESTING_EXCEPTIONS.getMarker(),
							"sigSet missing for round {}", lastRoundReceived);
					return sigSet;
				}
				if (sigSet.getSigInfo(selfId.getIdAsInt()) != null) {// selfId assumed to be main
					log.error(EXCEPTION.getMarker(),
							"recordSig called twice with selfId and round {}",
							lastRoundReceived);
					return sigSet;
				}

				if (platform.getAddressBook().getSize() == 1) {
					addSigInfo(signedState, sigInfo);
				} else {
					sigSet.addSigInfo(sigInfo);
				}

				log.debug(SIGNED_STATE.getMarker(),
						"platform {} created sig for round {}", platform.getSelfId(),
						sigInfo.getRound());

				for (Iterator<SigInfo> iter = otherSigInfos.iterator(); iter
						.hasNext(); ) {
					SigInfo otherSigInfo = iter.next();
					if (otherSigInfo.getRound() == lastRoundReceived) {
						iter.remove();
						addSigInfo(signedState, otherSigInfo);
					}
				}
				log.debug(SIGNED_STATE.getMarker(),
						"platform {} added sigInfo from other nodes for round {}", platform.getSelfId(),
						sigInfo.getRound());
				//update the signature set first then delete old states

				removeOldStates();
			} else { // not signed by self
				// ignore rounds too far in the future or past
				if (sigSet != null) { // if self already signed this round, then collect this one
					log.debug(STATE_SIG_DIST.getMarker(),
							"platform {} got sig from {} for round {} and adding it",
							platform.getSelfId(), sigInfo.getMemberId(),
							sigInfo.getRound());
					addSigInfo(signedState, sigInfo);
				} else { // not yet collecting this round, so hold on to it for a while.

					if (!allStates.isEmpty() && allStates.keySet().iterator()
							.next() <= sigInfo.getRound()) {
						otherSigInfos.add(sigInfo);

						log.debug(STATE_SIG_DIST.getMarker(),
								"platform {} got sig from {} for round {} and keeping it until own sig",
								platform.getSelfId(), sigInfo.getMemberId(),
								sigInfo.getRound());
					} else {
						log.debug(STATE_SIG_DIST.getMarker(),
								"platform {} got sig from {} for round {} but set is discarded",
								platform.getSelfId(), sigInfo.getMemberId(),
								sigInfo.getRound());
					}

				}
			}
			return sigSet;
		}
	}

	/** add the given SigInfo (not by self) to the given SigSet that already includes a self sig */
	void addSigInfo(SignedState signedState, SigInfo sigInfo) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::addSigInfo")) {
/** is self the only member in the network? */
			boolean singleMember = (1 == platform.getAddressBook().getSize());
			SigSet sigSet = signedState.getSigSet();
			if (sigSet.getSigInfo((int) sigInfo.getMemberId()) != null) {
				// we already have this signature so nothing should be done
				return;
			}

			// public key of the other member who signed
			PublicKey key = platform.getAddress(sigInfo.getMemberId())
					.getSigPublicKey();

			// the signature info from self
			SigInfo selfSigInfo = singleMember ? sigInfo : sigSet.getSigInfo(platform.getSelfId().getIdAsInt());

			// verify that the other member's signature is a valid signature of the hash found by self
			// if multiple for the same (round,member), only count 1
			boolean valid = false;
			try {
				// verify signatures from others, but not from self
				if (stateSettings.isEnableStateRecovery()) {
					//recover mode no need to collect signatures
					return;
				} else {
					valid = singleMember ? true
							: crypto.verifySignatureParallel(selfSigInfo.getHash(),
							sigInfo.getSig(), key, (Boolean b) -> {
							}).get(); // do the verification in parallel, but wait until it's done
				}
			} catch (InterruptedException | ExecutionException e) {
				// Such exceptions are acceptable for reconnect node,
				// because when a node falls behind, EventFlow.stopAndClear() would be called,
				// which stops all the threads in EventFlow and clears out all the data,
				// thus causes InterruptedException
				log.error(TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT_NODE.getMarker(),
						"error while verifying signature:", e);
				return;
			}

			if (valid) {
				// it verified so save it (otherwise, it's discarded)
				sigSet.addSigInfo(sigInfo);
				if (sigSet.isNewlyComplete()) {
					// at this point the signed state has the majority of signatures for the first time
					signedState.reserveState();
					if (!newSignedStateQueue.offer(signedState)) {
						signedState.releaseState();
						log.error(SIGNED_STATE.getMarker(),
								"Offer Failed - NewSignedStateQueue [ round = {} ]",
								signedState::getLastRoundReceived);
					}

					if (signedState.shouldSaveToDisk()) {
						signedState.weakReserveState();
						if (signedStateFileManager.saveSignedStateToDisk(signedState)) {
							lastSaveStateTimeMS = System.currentTimeMillis();
							if (!savedToDisk.offer(signedState.getLastRoundReceived())) {
								log.warn(SIGNED_STATE.getMarker(), "Offer Failed - SavedToDisk [ round = {} ]",
										signedState::getLastRoundReceived);
							}
						} else {
							signedState.weakReleaseState();
						}
					}
				}

				// we have just added a new signature, so we must check if it was the last one added in order to update
				// the statistics
				if (sigSet.hasAllSigs()) {
					platform.recordStatsValue(SwirldsPlatform.StatsType.AVGSTATESIGS, sigSet.getCount());
				}

				if (sigSet.getCount() == sigSet.getNumMembers()) {
					log.debug(STATE_SIG_DIST.getMarker(),
							"platform {} got all sigs for round {}",
							platform::getSelfId, selfSigInfo::getRound);
				}

				if (sigSet.isComplete() && //
						(lastCompleteSignedState == null || //
								selfSigInfo.getRound() > lastCompleteSignedState.getLastRoundReceived())) {
					setLastCompleteSignedState(signedState);
					log.info(LAST_COMPLETE_SIGNED_STATE.getMarker(),
							"set lastCompleteSignedState, lastRoundReceived: {}",
							lastCompleteSignedState.getLastRoundReceived());
					if (lastIncompleteSignedState != null
							&& lastCompleteSignedState.getLastRoundReceived() == lastIncompleteSignedState
							.getLastRoundReceived()) {
						findLastIncompleteSignedState();
					}
				}

				// Added to support dumping signed state JSON files after a reconnect
				// This is enabled/disabled by a setting which defaults to disabled and is okay to leave in prod code
				jsonifySignedState(signedState, StateDumpSource.STATE_SIGNED);
			} else {
				handleInvalidSignedState(signedState, sigInfo);
				dumpSignedState(signedState);

				// Added to support dumping signed state JSON files after an invalid signed state is detected
				// This is enabled/disabled by a setting which defaults to disabled and is okay to leave in prod code
				jsonifySignedState(signedState, StateDumpSource.INVALID_SIG);
			}
		}
	}

	/**
	 * Writes a {@link SignedState} to disk if enabled via the {@link StateSettings#dumpStateOnISS} setting. This method
	 * will only write a {@link SignedState} to disk once every {@link StateSettings#secondsBetweenISSDumps} seconds
	 * based on previous executions.
	 *
	 * <p>
	 * This method uses wall clock time on the local machine to control how frequently it writes {@link SignedState} to
	 * disk.
	 * </p>
	 *
	 * @param state
	 * 		the {@link SignedState} to be written to disk
	 */
	public void dumpSignedState(SignedState state) {

		if (!stateSettings.dumpStateOnISS) {
			return;
		}
		double currentTimeSeconds = System.currentTimeMillis() / 1000.0;
		double timeElapsed = currentTimeSeconds - lastISSDumpTimestampSeconds;
		if (timeElapsed < stateSettings.secondsBetweenISSDumps) {
			return;
		}
		lastISSDumpTimestampSeconds = currentTimeSeconds;

		state.weakReserveState();
		signedStateFileManager.saveSignedStateToDisk(state);
	}


	/**
	 * Diagnostic utility method for writing JSON versions of the {@link SignedState}, {@link SwirldState}, and {@code
	 * Hashgraph} contents. The JSON files are written in the saved state folders located under the {@code data/saved}
	 * path and are organized by node ID and round numbers.
	 *
	 * <p>
	 * NOTE: This utility is controlled via the {@link JsonExporterSettings} and different features maybe
	 * enabled/disabled individually. Please see the javadoc on the {@link JsonExporterSettings} interface for
	 * additional details.
	 * </p>
	 *
	 * @param state
	 * 		the signed state instance to be serialized as a JSON file
	 * @param source
	 * 		an enumeration value indicated the calling platform feature that requested the export
	 */
	public void jsonifySignedState(final SignedState state, final StateDumpSource source) {

		boolean shouldDump = jsonExporterSettings.isActive();
		boolean onDisk = false;

		switch (source) {
			case INVALID_SIG:
				jsonDumpContinually.set(false);
				break;
			case RECONNECT:
				jsonDumpContinually.set(jsonExporterSettings.isWriteContinuallyEnabled());
				break;
			case STATE_SIGNED:
				shouldDump = jsonDumpContinually.get();
				break;
			case DISK_WRITE:
				shouldDump = jsonExporterSettings.isActive() && jsonExporterSettings.isWriteWithSavedStateEnabled();
				break;
			case DISK_LOAD:
				shouldDump = jsonExporterSettings.isActive() && jsonExporterSettings.isWriteWithSavedStateEnabled();
				onDisk = true;
				break;
			case USER_REQUEST:
			default:
				break;
		}

		if (shouldDump) {
			try {
				if (jsonExporterSettings.isHashgraphExportEnabled()) {
					signedStateFileManager.jsonify(state.getLastRoundReceived(), platform.getAllEvents(), onDisk);
				}

				if (jsonExporterSettings.isSignedStateExportEnabled()) {
					signedStateFileManager.jsonify(state, onDisk);
				}

				if (jsonExporterSettings.isSwirldStateExportEnabled()) {
					signedStateFileManager.jsonify(state.getLastRoundReceived(), state.getState(), onDisk);
				}
			} catch (IOException ex) {
				log.info(TESTING_EXCEPTIONS.getMarker(), "Error while writing JSON signed state", ex);
			}
		}

	}

	// This may be used for a recovery feature in the future.
	public void saveLastCompleteStateToDisk() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::saveLastCompleteStateToDisk")) {
// check if we have a complete state and check if we already saved this state to disk
			if (lastCompleteSignedState != null && savedToDisk.peekLast() != lastCompleteSignedState.getLastRoundReceived()) {
				lastCompleteSignedState.weakReserveState();
				if (signedStateFileManager.saveSignedStateToDisk(lastCompleteSignedState)) {
					if (!savedToDisk.offer(lastCompleteSignedState.getLastRoundReceived())) {
						log.warn(STATE_ON_DISK_QUEUE.getMarker(),
								"Offer Failed - saveLastCompleteStateToDisk [ round = {} ]",
								lastCompleteSignedState::getLastRoundReceived);
					}
				} else {
					lastCompleteSignedState.weakReleaseState();
				}
			}
		}
	}

	public void addCompleteSignedState(SignedState signedState, boolean onDisk) {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::addCompleteSignedState")) {
			signedState.setSavedToDisk(onDisk);
			allStates.put(signedState.getLastRoundReceived(), signedState);
			lastSignedStateTimestamp = signedState.getConsensusTimestamp();
			lastHashgraphConsTime = signedState.getConsensusTimestamp();
			if (lastCompleteSignedState == null
					|| lastCompleteSignedState.getLastRoundReceived() < signedState.getLastRoundReceived()) {
				setLastCompleteSignedState(signedState);
				log.info(LAST_COMPLETE_SIGNED_STATE.getMarker(),
						"set lastCompleteSignedState, lastRoundReceived: {}",
						lastCompleteSignedState.getLastRoundReceived());
			}
			if (onDisk) {
				if (!savedToDisk.offer(signedState.getLastRoundReceived())) {
					log.warn(STATE_ON_DISK_QUEUE.getMarker(),
							"Offer Failed - addCompleteSignedState [ round = {} ]",
							signedState::getLastRoundReceived);
				}
			}
			platform.getFreezeManager().loadedStateFromDisk(signedState.getConsensusTimestamp());
		}
	}

	/**
	 * Removes Signed states that we no longer need from memory. This method is private and is intended to
	 * be used internally
	 */
	private void removeOldStates() {
		try (ResourceLock locker = lock.lockAsResource("SignedStateManager::removeOldStates")) {
// if the number of signature sets we are keeping is higher than signedStateKeep, iterate over the
			// map and remove the oldest signature sets until there are no more than signedStateKeep.
			Iterator<SignedState> it = allStates.values().iterator();
			int size = allStates.size();
			log.debug(SIGNED_STATE.getMarker(),
					"platform {} is about to remove old states, allStates size: {}", platform.getSelfId(), size);
			while (size > stateSettings.getSignedStateKeep()
					&& it.hasNext()) {
				SignedState next = it.next();
				boolean remove = false;
				// If the signed state is lastCompleteSignedState, we do not delete it
				if (next.getLastRoundReceived() == getLastCompleteRound()) {
					continue;
				} else if (!next.shouldSaveToDisk()) {
					// we are deleting this state from this list. if it is being saved to disk, then we will still
					// have it
					// in memory, and it will be deleted when it is removed from the savedToDisk list
					// send it to the queue to be deleted by the background thread
					garbageCollector.deleteBackground(next);
					remove = true;
				} else {
					// If a state needs to be saved to disk, we don't want to remove it until we get a complete set of
					// signatures. If we were to discard it, a node could miss saving a state to disk.
					if (next.getSigSet().isComplete() && next.isSavedToDisk()) {
						garbageCollector.deleteBackground(next);
						remove = true;
					}
				}

				if (remove) {
					// if this sigset has all sigs, it means its value has already been updated in the statistics. if it
					// doesn't, we will update the value before discarding it
					if (!next.getSigSet().hasAllSigs()) {
						platform.recordStatsValue(SwirldsPlatform.StatsType.AVGSTATESIGS, next.getSigSet().getCount());
					}
					it.remove();
					size--;
				}

			}
			log.debug(SIGNED_STATE.getMarker(),
					"finished putting old states to deletionQueue, allStates size: {}", size);

			log.debug(SIGNED_STATE.getMarker(),
					"platform {} is about to remove old SignedState on disk, savedToDisk size: {}",
					platform.getSelfId(), savedToDisk.size());
			// Also keep track of how many we have on disk, so we always have at least signedStateDisk.
			// Do not delete signed states on disk if we have dumped due to an ISS.
			while (savedToDisk.size() > stateSettings.getSignedStateDisk() && lastISSDumpTimestampSeconds == 0) {
				final Long forRemoval = savedToDisk.poll();

				signedStateFileManager.deleteSignedStateFromDisk(forRemoval);
			}
			log.debug(SIGNED_STATE.getMarker(),
					"finished putting into taskQueue, savedToDisk size: {}",
					savedToDisk.size());
		}
	}

	public int getNumStatesInMemory() {
		return allStates.size();
	}

	/**
	 * Internal utility method for logging an invalid state error and invoking the {@link InvalidSignedStateListener}.
	 *
	 * @param signedState
	 * 		the signed state which was determined to be invalid based on the signature
	 * @param sigInfo
	 * 		the signature and related information which did not match the signed state
	 */
	private void handleInvalidSignedState(final SignedState signedState, final SigInfo sigInfo) {
		final SignedStateLeaf ssLeaf = signedState.getLeaf();

		org.apache.logging.log4j.util.Supplier<String> eventsToString;
		if (!firstISSLogged) {
			eventsToString = () -> {
				StringBuilder sb = new StringBuilder();
				for (EventImpl event : ssLeaf.getEvents()) {
					sb.append(event.getBaseEventHashedData().toString());
					sb.append('\n');
					sb.append(event.getBaseEventUnhashedData().toString());
					sb.append('\n');
					sb.append(event.getConsensusData().toString());
					sb.append('\n');
				}
				return sb.toString();
			};
		} else {
			eventsToString = () -> "Events logged only on first ISS";
		}

		log.error(TESTING_EXCEPTIONS.getMarker(),
				"Exception: {} Received invalid state signature! round:{} memberId:{} details:\n"
						+ "hash: {}\n"//
						+ "consensusTimestamp: {}\n"//
						+ "numEventsCons: {}\n"//
						+ "hashEventsCons: {}\n"//
						+ "eventHashXor: {}\n"//
						+ "swirldStateHash: {}\n"//
						+ "addressBookHash: {}\n"
						+ "ssLeafHash: {}\n"
						+ "ssLeafConsensusTime: {}\n"
						+ "ssLeafRound: {}\n"
						+ "ssLeafNumEventsCons: {}\n"
						+ "ssLeafHashEventsCons: {}\n"
						+ "ssLeafMinGenInfo: {}\n"
						+ "ssLeafAddressBookHash: {}\n"
						+ "ssLeafEventCount: {}\n"
						+ "ssLeafLastTransactionTimestamp: {}\n"
						+ "events: \n{}\n",
				platform::getSelfId, sigInfo::getRound, sigInfo::getMemberId,
				signedState::getHash,
				signedState::getConsensusTimestamp,
				signedState::getNumEventsCons,
				() -> signedState.getHashEventsCons(),
				() -> hex(EventUtils.xorEventHashes(signedState.getEvents())),
				() -> hex(signedState.getSwirldStateHash()),
				() -> signedState.getAddressBook().getHash().toString(),

				ssLeaf::getHash,
				ssLeaf::getConsensusTimestamp,
				ssLeaf::getRound,
				ssLeaf::getNumEventsCons,
				() -> ssLeaf.getHashEventsCons(),
				ssLeaf::getMinGenInfo,
				() -> ssLeaf.getAddressBook().getHash(),
				() -> ssLeaf.getEvents().length,
				ssLeaf::getLastTransactionTimestamp,
				eventsToString
		);

		signedState.reserveState();
		try {
			notifySignedStateListeners(signedState, sigInfo);
		} finally {
			signedState.releaseState();
		}

		if (!firstISSLogged) {
			ssLeaf.compareSnapshot();
		}

		firstISSLogged = true;
	}
}

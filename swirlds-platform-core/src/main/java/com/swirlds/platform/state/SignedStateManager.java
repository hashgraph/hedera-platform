/*
 * (c) 2016-2021 Swirlds, Inc.
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

import com.swirlds.common.AddressBook;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.internal.JsonExporterSettings;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.threading.ThreadConfiguration;
import com.swirlds.common.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.transaction.internal.SystemTransactionBitsPerSecond;
import com.swirlds.common.transaction.internal.SystemTransactionPing;
import com.swirlds.logging.payloads.IssPayload;
import com.swirlds.platform.AbstractPlatform;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.components.StateSignatureRecorder;
import com.swirlds.platform.event.EventUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.common.CommonUtils.hex;
import static com.swirlds.common.Units.BITS_TO_BYTES;
import static com.swirlds.common.Units.MILLISECONDS_TO_SECONDS;
import static com.swirlds.common.Units.SECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.FREEZE;
import static com.swirlds.logging.LogMarker.LAST_COMPLETE_SIGNED_STATE;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.logging.LogMarker.STATE_ON_DISK_QUEUE;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT_NODE;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * Data structures and methods to manage the various signed states. That includes collecting signatures from
 * the other members, and storing/loading signed states to/from disk.
 */
public class SignedStateManager implements StateSignatureRecorder {

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

	/** A queue that stores events for a particular round that will be stored in the local state on disk */
	private final LinkedHashMap<Long, EventImpl[]> eventsForRound = new LinkedHashMap<>();

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
	private ReentrantLock lock;

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
	 * Acquire {@link #lock} and return an {@link AutoCloseableWrapper} around it that unlocks the lock.
	 */
	private AutoCloseableWrapper<ReentrantLock> getAutoCloseableLock() {
		lock.lock();
		return new AutoCloseableWrapper<>(lock, lock::unlock);
	}

	/**
	 * Mark a SignedState as the most recent completed signed state.
	 */
	private void setLastCompleteSignedState(SignedState ss) {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			if (lastCompleteSignedState != null) {
				lastCompleteSignedState.weakReleaseState();
			}
			if (ss != null) {
				ss.weakReserveState();
				// All signed states from earlier rounds can be archived now.
				for (SignedState state : allStates.values()) {
					if (!state.isMarkedForArchival() && state.getLastRoundReceived() < ss.getLastRoundReceived()) {
						state.markForArchival();
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
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
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
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			return lastCompleteSignedState == null ? -1 : lastCompleteSignedState.getLastRoundReceived();
		}
	}

	/**
	 * @return the round number of the latest round saved to disk
	 */
	public long getLastRoundSavedToDisk() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			return savedToDisk.size() == 0 ? -1 : savedToDisk.getLast();
		}
	}

	/** @return latest round for which we do NOT have a supermajority */
	public long getLastIncompleteRound() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			findLastIncompleteSignedState();
			return lastIncompleteSignedState.getLastRoundReceived();
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
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			findLastIncompleteSignedState();
			return lastIncompleteSignedState == null ? null : lastIncompleteSignedState.getSigSet().getSigInfosCopy();
		}
	}

	/**
	 * @return the latest complete signed state, or null if none are complete
	 */
	public AutoCloseableWrapper<SignedState> getLastCompleteSignedState() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			final SignedState latest = lastCompleteSignedState;

			final Runnable closeCallback = () -> {
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
	 * Returns the latest signed {#link SwirldState} signed by members with more than 1/3 of total stake.
	 *
	 * The {#link SwirldState} is returned in a {#link AutoCloseableWrapper} that <b>must</b> be use with
	 * a try-with-resources
	 *
	 * @param <T>
	 * 		A type extending from {#link SwirldState}
	 * @return the latest complete signed swirld state, or null if none are complete
	 */
	@SuppressWarnings("unchecked")
	public <T extends SwirldState> AutoCloseableWrapper<T> getLastCompleteSwirldState() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			final SignedState latest = lastCompleteSignedState;

			final Runnable closeCallback = () -> {
				if (latest != null) {
					latest.weakReleaseState();
				}
			};

			T swirldState = null;
			if (latest != null) {
				latest.weakReserveState();
				swirldState = (T) latest.getSwirldState();
			}


			return new AutoCloseableWrapper<>(swirldState, closeCallback);
		}
	}

	/**
	 * Return the consensus timestamp of the last signed state in the manager, might be null if there are no
	 * states
	 *
	 * @return the consensus timestamp of the last signed state
	 */
	public Instant getLastSignedStateConsensusTimestamp() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			final Instant result = lastSignedStateTimestamp;
			return result;
		}
	}

	/**
	 * set lastIncompleteRound to the last round signed by self but NOT by members with more than 2/3 of the total
	 * stake, or null if none exists
	 */
	private void findLastIncompleteSignedState() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
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

	/**
	 * Get the latest signed states. This method creates a copy, so no changes to the array will be made
	 *
	 * @return the latest signed states
	 */
	public SignedStateInfo[] getSignedStateInfo() {
		// It is assumed that all data in SignedStateInfo is safe to read even after a state has been
		// archived or deleted. If this is not the case then we need to reserve the states before releasing the info.
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			final SignedStateInfo[] result = allStates.values().toArray(new SignedState[0]);
			return result;
		}
	}

	/**
	 * Start empty, with no known signed states. The number of addresses in
	 * platform.hashgraph.getAddressBook() must not change in the future. The addressBook must contain
	 * exactly the set of members who can sign the state. A signed state is considered completed when it has
	 * signatures from members with 1/3 or more of the total stake.
	 *
	 * @param platform
	 * 		the Platform running this, such that platform.hashgraph.getAddressBook() containing
	 * 		exactly those members who can sign
	 * @param crypto
	 * 		an object holding all the public/private key pairs and the CSPRNG state for this member
	 * @param signedStateFileManager
	 * 		a manager which takes care of saving, deleting, and managing signed state files
	 * @param stateSettings
	 * 		settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors
	 * @param jsonExporterSettings
	 * 		settings that control the diagnostic export of state objects as JSON
	 */
	public SignedStateManager(final AbstractPlatform platform, final Crypto crypto,
			final SignedStateFileManager signedStateFileManager, final StateSettings stateSettings,
			final JsonExporterSettings jsonExporterSettings) {
		this(platform, crypto, signedStateFileManager, stateSettings,
				jsonExporterSettings, new SignedStateGarbageCollector(platform::getStats));
	}

	/**
	 * Start empty, with no known signed states. The number of addresses in
	 * platform.hashgraph.getAddressBook() must not change in the future. The addressBook must contain
	 * exactly the set of members who can sign the state. A signed state is considered completed when it has
	 * signatures from members with 1/3 or more of the total stake.
	 *
	 * @param platform
	 * 		the Platform running this, such that platform.hashgraph.getAddressBook() containing
	 * 		exactly those members who can sign
	 * @param crypto
	 * 		an object holding all the public/private key pairs and the CSPRNG state for this member
	 * @param signedStateFileManager
	 * 		a manager which takes care of saving, deleting, and managing signed state files
	 * @param stateSettings
	 * 		settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors
	 * @param jsonExporterSettings
	 * 		settings that control the diagnostic export of state objects as JSON
	 * @param signedStateGarbageCollector
	 * 		a garbage collector which deletes signed states which are not reserved for use
	 */
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

		newSignedStateThread = new ThreadConfiguration()
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("newSignedState")
				.setRunnable(new NewSignedStateRunnable(newSignedStateQueue, platform.getAppMain()))
				.build();
		newSignedStateThread.start();

		this.garbageCollector = signedStateGarbageCollector;

		garbageCollectorThread = new ThreadConfiguration()
				.setNodeId(selfId.getId())
				.setComponent(PLATFORM_THREAD_POOL_NAME)
				.setThreadName("signedStateGarbageCollector")
				.setRunnable(garbageCollector)
				.build();
		garbageCollectorThread.start();

		lock = new ReentrantLock(false);
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
				listener.notifyError(platform, signedState.getAddressBook(), signedState.getSwirldState(),
						signedState.getEvents(), platform.getSelfId(), new NodeId(true, sigInfo.getMemberId()),
						sigInfo.getRound(), signedState.getConsensusTimestamp(), signedState.getNumEventsCons(),
						signedState.getStateHashBytes(), signedState.getSwirldStateHashBytes());
			}
		}
	}

	public void addSignedStateListener(final InvalidSignedStateListener listener) {
		if (listener == null) {
			throw new IllegalArgumentException("listener");
		}

		invalidSignedStateListeners.add(listener);
	}

	private boolean shouldSaveToDisk(final Instant consensusTimestamp, final Instant lastConsTime) {
		// the first round should be saved to disk and every round which is about saveStatePeriod seconds after the
		// previous one should be saved. this will not always be exactly saveStatePeriod seconds after the previous one,
		// but it will be predictable at what time each a state will be saved
		boolean isSavePeriod = lastConsTime == null // the first round should be signed
				|| (stateSettings.getSaveStatePeriod() > 0 &&
				consensusTimestamp.getEpochSecond()
						/ stateSettings.getSaveStatePeriod() > lastConsTime.getEpochSecond()
						/ stateSettings.getSaveStatePeriod());

		return stateSettings.getSaveStatePeriod() > 0 && isSavePeriod;
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
		// The last state saved before the freeze period is always saved to disk
		// the first round should be saved to disk and every round which is about saveStatePeriod seconds after the
		// previous one should be saved.
		final boolean shouldSaveToDisk = ss.isFreezeState() || shouldSaveToDisk(ss.getConsensusTimestamp(),
				getLastSignedStateConsensusTimestamp());
		ss.setShouldSaveToDisk(shouldSaveToDisk);

		// Put the round number and signature of the SignedState into a system transaction.
		// There is no need to store the hash, since everyone should agree.
		// There is no need to sign the transaction, because the event will be signed.
		final byte[] sig = crypto.sign(ss.getStateHashBytes());
		log.info(SIGNED_STATE.getMarker(), "newSelfSigned:: get sig by calling crypto.sign()");

		final Transaction freezeTran = new StateSignatureTransaction(ss.isFreezeState(),
				ss.getLastRoundReceived(),
				sig);

		if (ss.getStateHashBytes().length != Crypto.HASH_SIZE_BYTES) {
			log.error(EXCEPTION.getMarker(),
					"hash.length = {} != {} = Crypto.HASH_SIZE_BYTES",
					ss.getStateHashBytes().length, Crypto.HASH_SIZE_BYTES);
		}

		// If beta mirror logic is enabled and this node is zero stake then do not attempt
		// to send the system transaction
		if (SettingsCommon.enableBetaMirror && platform.isZeroStakeNode()) {
			if (ss.isFreezeState()) {
				// if this is a freeze state, we should let zeroStake node enter maintenance mode
				platform.enterMaintenance();
			}
		} else {
			final boolean success = platform.createSystemTransaction(freezeTran);

			if (!success) {
				log.error(EXCEPTION.getMarker(),
						"failed to create signed state transaction)");
			}

			//send a transaction giving the average throughput sent from self to all others (in bits per second)
			if (SettingsCommon.enableBpsTrans) {
				final long[] avgBitsPerSecSent = new long[platform.getStats().avgBytePerSecSent.length];
				for (int i = 0; i < platform.getStats().avgBytePerSecSent.length; i++) {
					avgBitsPerSecSent[i] =
							(long) platform.getStats().avgBytePerSecSent[i].getCyclesPerSecond() * BITS_TO_BYTES;
				}
				final Transaction systemTransaction = new SystemTransactionBitsPerSecond(avgBitsPerSecSent);
				final boolean good = platform.createSystemTransaction(systemTransaction);
				if (!good) {
					log.error(EXCEPTION.getMarker(),
							"failed to create bits-per-second system transaction)");
				}
			}

			//send a transaction giving the average ping time from self to all others (in microseconds)
			if (SettingsCommon.enablePingTrans) {
				final int[] avgBitsPerSecSent = new int[platform.getStats().avgPingMilliseconds.length];
				for (int i = 0; i < platform.getStats().avgPingMilliseconds.length; i++) {
					avgBitsPerSecSent[i] = (int)
							(platform.getStats().avgPingMilliseconds[i].getWeightedMean() * MILLISECONDS_TO_SECONDS);
				}
				final Transaction systemTransaction = new SystemTransactionPing(avgBitsPerSecSent);
				final boolean good = platform.createSystemTransaction(systemTransaction);
				if (!good) {
					log.error(EXCEPTION.getMarker(),
							"failed to create bits-per-second system transaction)");
				}
			}
		}

		if (ss.isFreezeState()) {
			log.info(FREEZE.getMarker(),
					"Hashed state in freeze period. Freeze state is about to be saved to disk,last round is {}",
					ss.getLastRoundReceived());
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

		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			EventImpl[] localEvents = eventsForRound.get(ss.getLastRoundReceived());
			if (localEvents != null) {
				LocalStateEvents localStateEvents = new LocalStateEvents();
				localStateEvents.setEvents(localEvents);
				ss.setLocalStateEvents(localStateEvents);
			}
			eventsForRound.remove(ss.getLastRoundReceived());

			if (shouldSaveToDisk) {
				lastSSRoundGoingToDisk = ss.getLastRoundReceived();
			}
			// lastRoundFameDecided is the roundReceived for the last Event for which there is
			// consensus. So this is the round that the signed state represents, and is used as the key in the Map
			// allStates.
			allStates.put(ss.getLastRoundReceived(), ss);
			lastSignedStateTimestamp = ss.getConsensusTimestamp();

			return recordStateSig(ss.getLastRoundReceived(), platform.getSelfId().getId(), ss.getStateHashBytes(), sig);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SigSet recordStateSig(long lastRoundReceived, long memberId,
			byte[] hash, byte[] sig) {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
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
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			// is self the only member in the network?
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
					valid = singleMember || crypto.verifySignatureParallel(selfSigInfo.getHash(),
							sigInfo.getSig(), key, (Boolean b) -> {
							}).get(); // do the verification in parallel, but wait until it's done
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (ExecutionException e) {
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
		double currentTimeSeconds = (double) System.currentTimeMillis() * SECONDS_TO_MILLISECONDS;
		double timeElapsed = currentTimeSeconds - lastISSDumpTimestampSeconds;
		if (timeElapsed < stateSettings.secondsBetweenISSDumps) {
			return;
		}
		lastISSDumpTimestampSeconds = currentTimeSeconds;

		state.weakReserveState();
		signedStateFileManager.saveIssStateToDisk(state);
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
					signedStateFileManager.jsonify(state.getLastRoundReceived(), state.getSwirldState(), onDisk);
				}
			} catch (IOException ex) {
				log.info(TESTING_EXCEPTIONS.getMarker(), "Error while writing JSON signed state", ex);
			}
		}

	}

	// This may be used for a recovery feature in the future.
	public void saveLastCompleteStateToDisk() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			// check if we have a complete state and check if we already saved this state to disk
			if (lastCompleteSignedState != null &&
					savedToDisk.peekLast() != lastCompleteSignedState.getLastRoundReceived()) {
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
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			signedState.setSavedToDisk(onDisk);
			allStates.put(signedState.getLastRoundReceived(), signedState);
			lastSignedStateTimestamp = signedState.getConsensusTimestamp();
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
		}
	}

	/**
	 * Removes Signed states that we no longer need from memory. This method is private and is intended to
	 * be used internally
	 */
	private void removeOldStates() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
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

	/**
	 * Get the number of signed state currently in memroy
	 */
	public int getNumStatesInMemory() {
		return allStates.size();
	}

	/**
	 * Get the round number of the last signed state save to the disk
	 */
	public long getLastSSRoundGoingToDisk() {
		return lastSSRoundGoingToDisk;
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
		final PlatformState platformState = signedState.getState().getPlatformState();

		org.apache.logging.log4j.util.Supplier<String> eventsToString;
		if (!firstISSLogged) {
			eventsToString = () -> {
				StringBuilder sb = new StringBuilder();
				for (EventImpl event : signedState.getEvents()) {
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

		final AddressBook addressBook = signedState.getAddressBook();
		CryptoFactory.getInstance().digestSync(addressBook);

		log.error(TESTING_EXCEPTIONS.getMarker(),
				"Exception: {} Received invalid state signature! round:{} memberId:{} details:\n"
						+ "hash: {}\n"//
						+ "consensusTimestamp: {}\n"//
						+ "numEventsCons: {}\n"//
						+ "hashEventsCons: {}\n"//
						+ "eventHashXor: {}\n"//
						+ "swirldStateHash: {}\n"//
						+ "addressBookHash: {}\n"
						+ "platformStateHash: {}\n"
						+ "platformStateConsensusTime: {}\n"
						+ "platformStateRound: {}\n"
						+ "platformStateNumEventsCons: {}\n"
						+ "platformStateHashEventsCons: {}\n"
						+ "platformStateMinGenInfo: {}\n"
						+ "platformStateAddressBookHash: {}\n"
						+ "platformStateEventCount: {}\n"
						+ "platformStateLastTransactionTimestamp: {}\n"
						+ "events: \n{}\n",
				platform::getSelfId, sigInfo::getRound, sigInfo::getMemberId,
				signedState::getStateHash,
				signedState::getConsensusTimestamp,
				signedState::getNumEventsCons,
				signedState::getHashEventsCons,
				() -> hex(EventUtils.xorEventHashes(signedState.getEvents())),
				() -> hex(signedState.getSwirldStateHashBytes()),
				() -> signedState.getAddressBook().getHash().toString(),
				platformState::getHash,
				platformState::getConsensusTimestamp,
				platformState::getRound,
				platformState::getNumEventsCons,
				platformState::getHashEventsCons,
				platformState::getMinGenInfo,
				addressBook::getHash,
				() -> platformState.getEvents().length,
				platformState::getLastTransactionTimestamp,
				eventsToString
		);

		log.error(TESTING_EXCEPTIONS.getMarker(), () -> new IssPayload(
				signedState.getLastRoundReceived(),
				selfId.getId(),
				sigInfo.getMemberId()
		));

		signedState.reserveState();
		try {
			notifySignedStateListeners(signedState, sigInfo);
		} finally {
			signedState.releaseState();
		}

		if (!firstISSLogged) {
			platformState.compareSnapshot();
		}

		firstISSLogged = true;
	}
}

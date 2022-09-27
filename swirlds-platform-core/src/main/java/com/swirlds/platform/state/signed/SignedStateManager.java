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
package com.swirlds.platform.state.signed;

import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransactionBitsPerSecond;
import com.swirlds.common.system.transaction.internal.SystemTransactionPing;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.AbstractPlatform;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.components.StateSignatureRecorder;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.IssLogger;
import com.swirlds.platform.state.NewSignedStateRunnable;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.system.Fatal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static com.swirlds.common.utility.Units.BYTES_TO_BITS;
import static com.swirlds.common.utility.Units.MILLISECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.FREEZE;
import static com.swirlds.logging.LogMarker.LAST_COMPLETE_SIGNED_STATE;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;
import static com.swirlds.logging.LogMarker.STATE_SIG_DIST;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

/**
 * Data structures and methods to manage the various signed states. That includes collecting signatures from
 * the other members, and storing/loading signed states to/from disk.
 */
public class SignedStateManager implements StateSignatureRecorder, SignedStateSignatureCollector {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** A queue that stores events for a particular round that will be stored in the local state on disk */
	private final LinkedHashMap<Long, EventImpl[]> eventsForRound = new LinkedHashMap<>();
	/** instance in charge of deleting and archiving signed states */
	private final SignedStateGarbageCollector garbageCollector;
	/** a thread that runs the SignedStateGarbageCollector */
	private final Thread garbageCollectorThread;
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
	private final List<SigInfo> otherSigInfos = new LinkedList<>();
	/**
	 * mapping from round to SignedState for that round. Because it's linked it preserves the order in which
	 * they are added, this is useful for discarding old states.
	 *
	 * Any signed state taken from this data structure MUST be reserved if it is used outside of a synchronized method.
	 */
	private final LinkedHashMap<Long, SignedState> allStates = new LinkedHashMap<>();
	/** a list of states that are saved to disk */
	private final AtomicLong lastSavedRound = new AtomicLong(-1);
	/** the last SignedState round that is intended to be saved to disk */
	private volatile long lastSSRoundGoingToDisk = -1;
	/** the member ID for self */
	private final NodeId selfId;
	/**
	 * The Platform whose address book has all members. Those with more than 2/3 of total stake must sign to
	 * complete
	 */
	private final AbstractPlatform platform;
	/** used to sign states with the platforms signing private key */
	private final HashSigner signer;
	/** the timestamp of the last signed state held by the manager */
	private Instant lastSignedStateTimestamp = null;
	/** last time the state is saved to disk successfully in millisecond */
	private long lastSaveStateTimeMS;
	/** a thread that provides a new signed state to SwirldMain */
	private final Thread newSignedStateThread;
	/** a thread that writes, reads and deletes FastCopyable object to/from files */
	private final SignedStateFileManager signedStateFileManager;

	/** settings that control {@link SignedState} creation, deletion, and disk persistence */
	private final StateSettings stateSettings;

	/** track all registered {@link InvalidSignedStateListener} attached to this {@link SignedStateManager} instance */
	private final List<InvalidSignedStateListener> invalidSignedStateListeners;

	/**
	 * A queue that is constantly polled by newSignedStateThread. This queue does not have a capacity like a regular
	 * queue. newSignedStateThread needs to be waiting for an object in order for it to be inserted.
	 *
	 * A state is reserved before it is inserted into this queue.
	 */
	private final BlockingQueue<SignedState> newSignedStateQueue = new SynchronousQueue<>();

	/**
	 * the lock providing concurrency control for the {@link #setLastCompleteSignedState(SignedState)} and {@link
	 * #setLastIncompleteSignedState(SignedState)} methods.
	 */
	private final ReentrantLock lock;

	/**
	 * the last wall clock time that the {@link #dumpSignedState(SignedState)} method wrote a {@link SignedState} to
	 * disk
	 */
	private double lastISSDumpTimestampSeconds;

	private final IssLogger issLogger;

	private final Runnable freezeEventCreation;


	/**
	 * Start empty, with no known signed states. The number of addresses in
	 * platform.hashgraph.getAddressBook() must not change in the future. The addressBook must contain
	 * exactly the set of members who can sign the state. A signed state is considered completed when it has
	 * signatures from members with 1/3 or more of the total stake.
	 *
	 * @param platform
	 * 		the Platform running this, such that platform.hashgraph.getAddressBook() containing
	 * 		exactly those members who can sign
	 * @param signer
	 * 		used to sign states with the platforms signing private key
	 * @param signedStateFileManager
	 * 		a manager which takes care of saving, deleting, and managing signed state files
	 * @param freezeEventCreation
	 * 		a runnable that prevents future events from being created
	 * @param stateSettings
	 * 		settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors
	 */
	public SignedStateManager(final AbstractPlatform platform, final HashSigner signer,
			final SignedStateFileManager signedStateFileManager, final Runnable freezeEventCreation,
			final StateSettings stateSettings) {
		this(platform, signer, signedStateFileManager, freezeEventCreation, stateSettings,
				new SignedStateGarbageCollector(platform::getStats, stateSettings));
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
	 * @param signer
	 * 		used to sign states with the platforms signing private key
	 * @param signedStateFileManager
	 * 		a manager which takes care of saving, deleting, and managing signed state files
	 * @param freezeEventCreation
	 * 		a runnable that prevents future events from being created
	 * @param stateSettings
	 * 		settings that control the {@link SignedStateManager} and {@link SignedStateFileManager} behaviors
	 * @param signedStateGarbageCollector
	 * 		a garbage collector which deletes signed states which are not reserved for use
	 */
	protected SignedStateManager(final AbstractPlatform platform, final HashSigner signer,
			final SignedStateFileManager signedStateFileManager,
			final Runnable freezeEventCreation, final StateSettings stateSettings,
			final SignedStateGarbageCollector signedStateGarbageCollector) {

		this.selfId = platform.getSelfId();
		this.platform = platform;
		this.signer = signer;
		this.freezeEventCreation = freezeEventCreation;

		this.issLogger = new IssLogger(platform.getSelfId().getId(), platform.getStats());

		this.signedStateFileManager = signedStateFileManager;
		this.stateSettings = stateSettings;

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

		NotificationFactory.getEngine().register(Fatal.FatalListener.class, this::fatalListener);
	}

	/**
	 * This method is called if there is a fatal error.
	 *
	 * @param notification
	 * 		the fatal notification object
	 */
	private void fatalListener(final Fatal.FatalNotification notification) {
		if (stateSettings.dumpStateOnFatal) {
			try (final AutoCloseableWrapper<SignedState> wrapper = getLastCompleteSignedState()) {
				final SignedState state = wrapper.get();
				if (state != null) {
					signedStateFileManager.saveFatalStateToDisk(state);
				}
			} catch (final InterruptedException e) {
				log.error(EXCEPTION.getMarker(), "interrupted while attempting to save state");
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Acquire {@link #lock} and return an {@link AutoCloseableWrapper} around it that unlocks the lock.
	 */
	private AutoCloseableWrapper<ReentrantLock> getAutoCloseableLock() {
		lock.lock();
		return new AutoCloseableWrapper<>(lock, lock::unlock);
	}

	/**
	 * Mark a SignedState as the last incomplete signed state.
	 */
	private void setLastIncompleteSignedState(final SignedState ss) {
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
		return lastSavedRound.get();
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
	 * Mark a SignedState as the most recent completed signed state.
	 */
	private void setLastCompleteSignedState(final SignedState ss) {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			if (lastCompleteSignedState != null) {
				lastCompleteSignedState.weakReleaseState();
			}
			if (ss != null) {
				ss.weakReserveState();
				// All signed states from earlier rounds can be archived now.
				for (final SignedState state : allStates.values()) {
					if (!state.isMarkedForArchival()
							&& state.isComplete()
							&& state.getLastRoundReceived() < ss.getLastRoundReceived()) {
						state.markForArchival();
						garbageCollector.archiveBackground(state);
					}
				}
			}
			lastCompleteSignedState = ss;
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
					latest.releaseState();
				}
			};

			T swirldState = null;
			if (latest != null) {
				latest.reserveState();
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
			for (final SignedState s : allStates.values()) {
				final long last = s.getLastRoundReceived();
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
	 * Invokes the invalid signed state listeners.
	 *
	 * @param signedState
	 * 		the local signed state instance whose signature failed to match the remote peer
	 * @param sigInfo
	 * 		the signatures received from the remote peer
	 */
	public void notifySignedStateListeners(final SignedState signedState, final SigInfo sigInfo) {
		for (final InvalidSignedStateListener listener : invalidSignedStateListeners) {
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
		final boolean isSavePeriod = lastConsTime == null // the first round should be signed
				|| (stateSettings.getSaveStatePeriod() > 0 &&
				consensusTimestamp.getEpochSecond()
						/ stateSettings.getSaveStatePeriod() > lastConsTime.getEpochSecond()
						/ stateSettings.getSaveStatePeriod());

		return stateSettings.getSaveStatePeriod() > 0 && isSavePeriod;
	}

	/**
	 * Keep a new signed state, signed only by self, and start collecting signatures for it.
	 *
	 * @param ss
	 * 		the signed state to be kept by the manager
	 */
	@Override
	public SigSet collectSignatures(final SignedState ss) {
		// The last state saved before the freeze period is always saved to disk
		// the first round should be saved to disk and every round which is about saveStatePeriod seconds after the
		// previous one should be saved.
		final boolean shouldSaveToDisk = ss.isFreezeState() || shouldSaveToDisk(ss.getConsensusTimestamp(),
				getLastSignedStateConsensusTimestamp());
		ss.setShouldSaveToDisk(shouldSaveToDisk);

		// Put the round number and signature of the SignedState into a system transaction.
		// There is no need to store the hash, since everyone should agree.
		// There is no need to sign the transaction, because the event will be signed.
		final Hash stateHash = ss.getStateHash();
		final byte[] sig = signer.sign(stateHash);
		log.info(SIGNED_STATE.getMarker(), "newSelfSigned:: get sig by calling crypto.sign()");


		if (stateHash.getValue().length != CryptoConstants.HASH_SIZE_BYTES) {
			log.error(EXCEPTION.getMarker(),
					"hash.length = {} != {} = Crypto.HASH_SIZE_BYTES",
					stateHash.getValue().length, CryptoConstants.HASH_SIZE_BYTES);
		}

		// If beta mirror logic is enabled and this node is zero stake then do not attempt
		// to send the system transaction
		if (SettingsCommon.enableBetaMirror && platform.isZeroStakeNode()) {
			if (ss.isFreezeState()) {
				// no reason to keep creating events because there is no signature transaction to send
				freezeEventCreation.run();
			}
		} else {
			final SystemTransaction sigTrans = new StateSignatureTransaction(ss.getLastRoundReceived(), sig);
			final boolean success = platform.createSystemTransaction(sigTrans);

			if (!success) {
				log.error(EXCEPTION.getMarker(),
						"failed to create signed state transaction)");
			}

			//send a transaction giving the average throughput sent from self to all others (in bits per second)
			if (SettingsCommon.enableBpsTrans) {
				final long[] avgBitsPerSecSent = new long[platform.getStats().avgBytePerSecSent.length];
				for (int i = 0; i < platform.getStats().avgBytePerSecSent.length; i++) {
					avgBitsPerSecSent[i] =
							(long) platform.getStats().avgBytePerSecSent[i].get() * BYTES_TO_BITS;
				}
				final SystemTransaction systemTransaction = new SystemTransactionBitsPerSecond(avgBitsPerSecSent);
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
							(platform.getStats().avgPingMilliseconds[i].get() * MILLISECONDS_TO_SECONDS);
				}
				final SystemTransaction systemTransaction = new SystemTransactionPing(avgBitsPerSecSent);
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
						"During State Recover offer Failed - NewSignedStateQueue [ round = {} ]",
						ss::getLastRoundReceived);
			}
			// save the last recovered state, even state save period requirement is not met
			if (shouldSaveToDisk || ss.getLastRoundReceived() >= platform.getRoundOfLastRecoveredEvent()) {
				signedStateFileManager.saveSignedStateToDisk(ss);
				lastSignedStateTimestamp = ss.getConsensusTimestamp();
			}
			//no need to collect & record signatures in sigSet in recover mode
			return null;
		}

		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
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
	public SigSet recordStateSig(final long lastRoundReceived, final long memberId,
			final byte[] hash, final byte[] sig) {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			final SigInfo sigInfo = new SigInfo(lastRoundReceived, memberId, hash, sig);
			final SignedState signedState = allStates.get(lastRoundReceived);
			final SigSet sigSet = signedState == null ? null : signedState.getSigSet();
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

				for (final Iterator<SigInfo> iter = otherSigInfos.iterator(); iter
						.hasNext(); ) {
					final SigInfo otherSigInfo = iter.next();
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
	void addSigInfo(final SignedState signedState, final SigInfo sigInfo) {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			// is self the only member in the network?
			final boolean singleMember = (1 == platform.getAddressBook().getSize());
			final SigSet sigSet = signedState.getSigSet();
			if (sigSet.getSigInfo((int) sigInfo.getMemberId()) != null) {
				// we already have this signature so nothing should be done
				return;
			}

			// public key of the other member who signed
			final PublicKey key = platform.getAddress(sigInfo.getMemberId())
					.getSigPublicKey();

			// verify that the other member's signature is a valid signature of the hash found by self
			// if multiple for the same (round,member), only count 1
			boolean valid = false;
			// verify signatures from others, but not from self
			if (stateSettings.isEnableStateRecovery()) {
				//recover mode no need to collect signatures
				return;
			} else {
				valid = singleMember || CryptoStatic.verifySignature(
						signedState.getStateHashBytes(), sigInfo.getSig(), key);
			}

			issLogger.reportSignature(signedState, sigInfo.getMemberId(), valid);

			if (valid) {
				// it is verified so save it (otherwise, it's discarded)
				sigSet.addSigInfo(sigInfo);
				if (sigSet.isNewlyComplete()) {
					// at this point the signed state has the majority of signatures for the first time
					if (signedState.isFreezeState()) {
						log.info(FREEZE.getMarker(),
								"Collected enough signatures on the freeze state. Freezing event creation now.");
						freezeEventCreation.run();
					}

					signedState.reserveState();
					signedState.markAsComplete();
					if (!newSignedStateQueue.offer(signedState)) {
						signedState.releaseState();
						log.error(SIGNED_STATE.getMarker(),
								"Offer Failed - NewSignedStateQueue [ round = {} ]",
								signedState::getLastRoundReceived);
					}

					if (signedState.shouldSaveToDisk()) {
						if (signedStateFileManager.saveSignedStateToDisk(signedState)) {
							lastSaveStateTimeMS = System.currentTimeMillis();
							lastSavedRound.set(signedState.getLastRoundReceived());
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
							platform::getSelfId, signedState::getLastRoundReceived);
				}

				if (sigSet.isComplete() &&
						(lastCompleteSignedState == null ||
								signedState.getLastRoundReceived() > lastCompleteSignedState.getLastRoundReceived())) {
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
			} else {
				signedState.reserveState();
				try {
					notifySignedStateListeners(signedState, sigInfo);
				} finally {
					signedState.releaseState();
				}
				dumpSignedState(signedState);
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
	public void dumpSignedState(final SignedState state) {
		if (!stateSettings.dumpStateOnISS) {
			return;
		}
		final double currentTimeSeconds = System.currentTimeMillis() * MILLISECONDS_TO_SECONDS;
		final double timeElapsed = currentTimeSeconds - lastISSDumpTimestampSeconds;
		if (timeElapsed < stateSettings.secondsBetweenISSDumps) {
			return;
		}
		lastISSDumpTimestampSeconds = currentTimeSeconds;

		signedStateFileManager.saveIssStateToDisk(state);
	}

	// This may be used for a recovery feature in the future.
	public void saveLastCompleteStateToDisk() {
		try (final AutoCloseableWrapper<ReentrantLock> autoLock = getAutoCloseableLock()) {
			// check if we have a complete state and check if we already saved this state to disk
			if (lastCompleteSignedState != null &&
					lastSavedRound.get() != lastCompleteSignedState.getLastRoundReceived()) {
				if (signedStateFileManager.saveSignedStateToDisk(lastCompleteSignedState)) {
					lastSavedRound.set(lastCompleteSignedState.getLastRoundReceived());
				}
			}
		}
	}

	public void addCompleteSignedState(final SignedState signedState, final boolean onDisk) {
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
				lastSavedRound.set(signedState.getLastRoundReceived());
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
			final Iterator<SignedState> it = allStates.values().iterator();
			int size = allStates.size();
			log.debug(SIGNED_STATE.getMarker(),
					"platform {} is about to remove old states, allStates size: {}", platform.getSelfId(), size);
			while (size > stateSettings.getSignedStateKeep()
					&& it.hasNext()) {
				final SignedState next = it.next();
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
}

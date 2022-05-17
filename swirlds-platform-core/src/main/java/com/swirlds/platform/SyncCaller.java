/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */
package com.swirlds.platform;

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.StartupTime;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.locks.MaybeLocked;
import com.swirlds.common.locks.MaybeLockedResource;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectLoadFailurePayload;
import com.swirlds.logging.payloads.ReconnectPeerInfoPayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.logging.payloads.UnableToReconnectPayload;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.reconnect.ReconnectLearner;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SigSet;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.SYNC_START;
import static com.swirlds.platform.state.PlatformState.getInfoString;

/**
 * A thread can run this to repeatedly initiate syncs with other members.
 */
class SyncCaller implements Runnable {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** ID number for this caller thread (0 is the first created by this platform, 1 next, etc) */
	final int callerNumber;
	/** the Platform object that is using this to call other members */
	private final AbstractPlatform platform;
	/** the address book with all member IP addresses and ports, etc. */
	private final AddressBook addressBook;
	/** the member ID for self */
	private final NodeId selfId;
	/** used to sign states with the platforms signing private key */
	private final HashSigner signer;

	/**
	 * The number of times reconnect has failed since the last succesfull reconnect.
	 */
	private int failedReconnectsInARow;

	/**
	 * The platform instantiates this, and gives it the self ID number, plus other info that will be useful
	 * to it. The platform can then create a thread to call run(). It will then repeatedly initiate syncs
	 * with others.
	 *
	 * @param platform
	 * 		the platform using this caller
	 * @param addressBook
	 * 		the address book listing who can be called
	 * @param selfId
	 * 		the ID number for self /** ID number for this caller thread (0 is the first created by
	 * 		this platform, 1 next, etc)
	 * @param callerNumber
	 * 		0 for the first caller thread created by this platform, 1 for the next, etc
	 * @param signer
	 * 		used to sign states with the platforms signing private key
	 */
	public SyncCaller(final AbstractPlatform platform, final AddressBook addressBook, final NodeId selfId,
			final int callerNumber, final HashSigner signer) {
		this.platform = platform;
		this.addressBook = addressBook;
		this.selfId = selfId;
		this.callerNumber = callerNumber;
		this.failedReconnectsInARow = 0;
		this.signer = signer;
	}

	/**
	 * The state used to reconnect may not be hashed.
	 */
	private static void hashStateForReconnect(final State workingState) {
		try {
			CryptoFactory.getInstance().digestTreeAsync(workingState).get();
		} catch (final ExecutionException e) {
			log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
					"Error encountered while hashing state for reconnect",
					ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
			throw new ReconnectException(e);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
					"Interrupted while attempting to hash state",
					ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
		}
	}

	/**
	 * repeatedly call others and sync with them
	 */
	@Override
	public void run() {
		int failedAttempts = 0;
		while (true) { // loop forever until the user quits the browser
			try {
				// choose a member at random, and sync with them
				final long otherId = callRequestSync();
				if (otherId >= 0) { // successful sync
					failedAttempts = 0;
					sleepAfterSync();
				} else {
					failedAttempts++;
					if (failedAttempts >= Settings.callerSkipsBeforeSleep) {
						failedAttempts = 0;
						try {
							// Necessary to slow down the attempts after N failures
							Thread.sleep(Settings.sleepCallerSkips);
							platform.getStats().sleep1perSecond.cycle();
						} catch (final InterruptedException ex) {
							Thread.currentThread().interrupt();
							return;
						} catch (final Exception ex) {
							// Suppress any exception thrown by the stats update
						}
					}
				}
			} catch (final Exception e) {
				failedAttempts++;
				log.error(EXCEPTION.getMarker(), "SyncCaller.run error", e);
			}
		}
	}

	/**
	 * If configured, sleeps a defined amount of time after a successful sync
	 *
	 * @throws InterruptedException
	 * 		if the thread is interrupted
	 */
	private void sleepAfterSync() throws InterruptedException {
		if (platform.getSleepAfterSync() > 0) {
			Thread.sleep(platform.getSleepAfterSync());
		}
	}

	/**
	 * Chose another member at random, request a sync with them, and perform one sync if they accept the
	 * request. A -1 is returned if the sync did not happen for some reason, such as the chosen member
	 * wasn't connected, or had a communication error, or was already syncing by calling self, or the lock
	 * was still held by the heartbeat thread, or some other reason.
	 *
	 * @return member ID of the member the attempted sync was with, or -1 if no sync happened
	 */
	private int callRequestSync() {
		int otherId = -1; // the ID of the member that I am syncing with now
		try { // catch any exceptions, log them, and ignore them
			if (platform.getSyncManager().hasFallenBehind()) {
				// we will not sync if we have fallen behind
				if (callerNumber == 0) {
					// caller number 0 will do the reconnect, the others will wait
					final boolean reconnected = doReconnect();
					// if reconnect failed, we will start over
					if (!reconnected) {
						return -1;
					}
					// if reconnect succeeded, we should continue with a sync
				} else {
					return -1;
				}
				log.debug(RECONNECT.getMarker(),
						"`callRequestSync` : node {} fell behind and has reconnected, thread ID callerNumber = {}",
						selfId,
						callerNumber);
			}


			// check transThrottle, if there is no reason to call, don't call anyone
			if (!platform.getSyncManager().transThrottle()) {
				return -1;
			}

			// check with sync manager for any reasons not to sync
			if (!platform.getSyncManager().shouldInitiateSync()) {
				return -1;
			}

			if (addressBook.getSize() == 1 && callerNumber == 0) {
				platform.checkPlatformStatus();
				// Only one member exists (self), so create an event and add it to the hashgraph.
				// Only one caller is allowed to do this (caller number 0).
				// This is like syncing with self, and then creating an event with otherParent being self.

				// self is the only member, so create an event for just this one transaction,
				// and immediately put it into the hashgraph. No syncing is needed.
				platform.getEventTaskCreator().createEvent(
						selfId.getId()/*selfId assumed to be main*/); // otherID (so self will count as the "other")
				Thread.sleep(50);
				platform.getSyncManager().successfulSync();
				// selfId assumed to be main
				return selfId.getIdAsInt(); // say that self just "synced" with self, and created an event for it
			}

			if (addressBook.getSize() <= 1) { // if there is no one to sync with, don't sync
				return -1;
			}

			// the sync manager will tell us who we need to call
			final List<Long> nodeList = platform.getSyncManager().getNeighborsToCall();

			// the array is sorted in ascending or from highest to lowest priority, so we go through the array and
			// try to
			// call each node until we are successful
			boolean syncAccepted = false;
			for (final Long aLong : nodeList) {
				otherId = aLong.intValue();
				// otherId is now the member to call

				log.debug(SYNC_START.getMarker(),
						"{} about to call {} (connection looks good)", selfId,
						otherId);

				try (final MaybeLocked lock = platform.getSimultaneousSyncThrottle().trySync(otherId, true)) {
					// Try to get both locks. If either is unavailable, then try the next node. Never block.
					if (!lock.isLockAcquired()) {
						continue;
					}

					try (final MaybeLockedResource<ConnectionManager> resource =
								 platform.getSharedConnectionLocks().tryLockConnection(NodeId.createMain(otherId))) {
						if (!resource.isLockAcquired()) {
							continue;
						}
						// check if we have a connection
						final SyncConnection conn = resource.getResource().getConnection();
						if (conn == null || !conn.connected()) {
							continue;
						}
						// try to initiate a sync. If they accept the request, then sync
						try {
							syncAccepted = platform.getShadowGraphSynchronizer().synchronize(conn);
							if (syncAccepted) {
								break;
							}

						} catch (final IOException e) {
							// IOException covers both SocketTimeoutException and EOFException, plus more
							log.error(SOCKET_EXCEPTIONS.getMarker(),
									"SyncCaller.sync SocketException (so incrementing iCSyncPerSec) while {} " +
											"listening for {}:",
									platform.getSelfId(), otherId, e);

							// close the connection, don't reconnect until needed.
							conn.disconnect();
						} catch (final Exception e) {
							// we use a different marker depending on what the root cause is
							log.error(NetworkUtils.determineExceptionMarker(e),
									"! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} " +
											"listening for {}:",
									platform.getSelfId(), otherId, e);

							// close the connection, don't reconnect until needed.
							conn.disconnect();
						}

					}
				}
			}

			if (syncAccepted) {
				platform.getSyncManager().successfulSync();
				return otherId;
			} else {
				return -1;
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn(EXCEPTION.getMarker(),
					"! SyncCaller.sync Interrupted (so incrementing iCSyncPerSec) while {} listening for {}:",
					platform.getSelfId(), otherId, e);

			return -1;
		} catch (final Exception e) {
			log.error(EXCEPTION.getMarker(),
					"! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} listening for {}:",
					platform.getSelfId(), otherId, e);
			return -1;
		}
	}

	/**
	 * Check if a reconnect is currently allowed. If not then kill the node.
	 */
	private void exitIfReconnectIsDisabled() {

		if (!Settings.reconnect.isActive()) {
			log.warn(STARTUP.getMarker(), () -> new UnableToReconnectPayload(
					"Node has fallen behind, reconnect is disabled, will die",
					platform.getSelfId().getIdAsInt()).toString());
			SystemUtils.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
		}

		if (Settings.reconnect.getReconnectWindowSeconds() >= 0 &&
				Settings.reconnect.getReconnectWindowSeconds() < StartupTime.getTimeSinceStartup().toSeconds()) {
			log.warn(STARTUP.getMarker(), () -> new UnableToReconnectPayload(
					"Node has fallen behind, reconnect is disabled outside of time window, will die",
					platform.getSelfId().getIdAsInt()).toString());
			SystemUtils.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
		}
	}

	/**
	 * Called to initiate the reconnect attempt by the {@link #callRequestSync()} method.
	 *
	 * @return true if the reconnect attempt completed successfully; otherwise false
	 */
	private boolean doReconnect() throws InterruptedException {
		exitIfReconnectIsDisabled();

		log.info(RECONNECT.getMarker(),
				"{} has fallen behind, will wait for all syncs to finish",
				platform.getSelfId());
		// wait and acquire all sync ongoing locks and release them immediately
		// this will ensure any ongoing sync are finished before we start reconnect
		// no new sync will start because we have a fallen behind status
		platform.getSimultaneousSyncThrottle().waitForAllSyncsToFinish();

		log.info(RECONNECT.getMarker(),
				"all ongoing syncs done, will stop and clear EventFlow, Hashgraph, and ShadowGraph");

		platform.getIntakeQueue().clear();
		platform.getEventMapper().clear();
		platform.getShadowGraph().clear();

		log.info(RECONNECT.getMarker(), "Hashgraph and shadow graph are now cleared");

		// clear Event Handlers after finish clearing RunningHashCalculator,
		// to make sure that forCons queue is empty during reconnect
		platform.prepareForReconnect();

		final List<Long> reconnectNeighbors = platform.getSyncManager().getNeighborsForReconnect();
		log.info(RECONNECT.getMarker(),
				"{} has fallen behind, will try to reconnect with {}",
				platform::getSelfId, reconnectNeighbors::toString);

		final State workingState = platform.getSwirldStateManager().getConsensusState();

		// Reserve the state
		workingState.incrementReferenceCount();

		// Hash the state if it has not yet been hashed
		hashStateForReconnect(workingState);

		try {
			return doReconnect(reconnectNeighbors, workingState);
		} finally {
			workingState.decrementReferenceCount();
		}
	}

	private boolean doReconnect(final List<Long> reconnectNeighbors, final State workingState) {

		final ReconnectPeerInfoPayload peerInfo = new ReconnectPeerInfoPayload();

		for (final Long neighborId : reconnectNeighbors) {
			SyncConnection conn = null;
			final SignedState signedState;
			// try to get the lock, it should be available if we have fallen behind
			try (final MaybeLockedResource<ConnectionManager> resource
						 = platform.getSharedConnectionLocks().tryLockConnection(NodeId.createMain(neighborId))) {
				if (!resource.isLockAcquired()) {
					peerInfo.addPeerInfo(neighborId, "failed to acquire lock, blocked by heartbeat thread");
					continue;
				}
				conn = resource.getResource().getConnection();
				if (conn == null || !conn.connected()) {
					// if we're not connected, try someone else
					peerInfo.addPeerInfo(neighborId, "peer unreachable, unable to establish connection");
					continue;
				}
				signedState = receiveStateFromNeighbor(neighborId, conn, workingState);

				if (signedState == null) {
					// The other node was unwilling to help this node to reconnect or the connection was broken
					peerInfo.addPeerInfo(neighborId, "no signed state received, peer declined request");
					continue;
				}
			} catch (final Exception e) {
				handleFailedReconnect(conn, peerInfo, neighborId, e);
				continue;
			}

			log.debug(RECONNECT.getMarker(), "{} `doReconnect` : finished, found peer node {}",
					platform.getSelfId(),
					conn.getOtherId());

			failedReconnectsInARow = 0;

			return reloadState(signedState);
		}

		log.info(RECONNECT.getMarker(),
				"{} `doReconnect` : finished, could not reconnect with any peer, reasons:\n{}",
				platform.getSelfId(),
				peerInfo
		);

		// if no nodes were found to reconnect with, return false
		return false;
	}

	private void handleFailedReconnect(final SyncConnection conn, final ReconnectPeerInfoPayload peerInfo,
			final Long neighborId, final Exception e) {
		if (Utilities.isOrCausedBySocketException(e)) {
			log.error(SOCKET_EXCEPTIONS.getMarker(), () -> new ReconnectFailurePayload(
					"Got socket exception while receiving a signed state!",
					ReconnectFailurePayload.CauseOfFailure.SOCKET).toString(), e);
		} else {
			log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
					"Error while receiving a signed state!",
					ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
			if (conn != null) {
				conn.disconnect();
			}
		}

		failedReconnectsInARow++;
		killNodeIfThresholdMet();

		// if we failed to receive a state from this node, we will try the next one
		peerInfo.addPeerInfo(neighborId, "exception occurred: " + e.getMessage());
	}

	private void killNodeIfThresholdMet() {
		if (failedReconnectsInARow >= Settings.reconnect.getMaximumReconnectFailuresBeforeShutdown()) {
			log.error(EXCEPTION.getMarker(), "Too many reconnect failures in a row, killing node");
			SystemUtils.exitSystem(SystemExitReason.RECONNECT_FAILURE);
		}
	}

	/**
	 * Attempts to receive a new signed state by reconnecting with the specified neighbor.
	 *
	 * @param neighborId
	 * 		the id of the neighbor from which to initiate reconnect
	 * @param conn
	 * 		the sync connection to use for the reconnect attempt
	 * @param initialState
	 * 		the initial signed state against which to perform a delta based reconnect
	 * @return the signed state received from the neighbor if the other node is willing to reconnect,
	 * 		or null if the other node is not willing to reconnect
	 * @throws ReconnectException
	 * 		if any error occurs during the reconnect attempt
	 */
	private SignedState receiveStateFromNeighbor(
			final Long neighborId,
			final SyncConnection conn,
			final State initialState) throws ReconnectException {
		final SignedState signedState;

		log.info(RECONNECT.getMarker(), () -> new ReconnectStartPayload(
				"Starting reconnect in role of the receiver.",
				true,
				platform.getSelfId().getIdAsInt(),
				neighborId.intValue(),
				platform.getSignedStateManager().getLastCompleteRound()).toString());

		final ReconnectLearner reconnect =
				new ReconnectLearner(conn,
						addressBook,
						initialState,
						platform.getCrypto(),
						Settings.reconnect.getAsyncStreamTimeoutMilliseconds(),
						platform.getStats().getReconnectStats());

		final boolean reconnectWasAttempted = reconnect.execute();
		if (!reconnectWasAttempted) {
			return null;
		}

		signedState = reconnect.getSignedState();
		final long lastRoundReceived = signedState.getLastRoundReceived();

		log.info(RECONNECT.getMarker(), () -> new ReconnectFinishPayload(
				"Finished reconnect in the role of the receiver.",
				true,
				platform.getSelfId().getIdAsInt(),
				neighborId.intValue(),
				lastRoundReceived).toString());

		log.info(RECONNECT.getMarker(), "Information for state received during reconnect:\n{}\n{}",
				() -> getInfoString(signedState.getState().getPlatformState()),
				() -> generateHashDebugString(signedState.getState(), StateSettings.getDebugHashDepth()));

		log.info(RECONNECT.getMarker(),
				"signed state events:\n{}", EventUtils.toShortStrings(signedState.getEvents()));

		return signedState;
	}

	/**
	 * Used by the {@link #doReconnect()} method to load the state received from the sender.
	 *
	 * @param signedState
	 * 		the signed state that was received from the sender
	 * @return true if the state was successfully loaded; otherwise false
	 */
	private boolean reloadState(final SignedState signedState) {
		log.debug(RECONNECT.getMarker(), "{} `reloadState` : reloading state", platform.getSelfId());

		// if the received state is null then we have failed to reconnect and must retry
		if (signedState == null) {
			return false;
		}

		// the state was received, so now we load it's data into different objects
		try {

			// Inject a self signature
			final byte[] signature = signer.sign(signedState.getStateHash());
			final SigInfo selfSigInfo = new SigInfo(signedState.getLastRoundReceived(),
					platform.getSelfId().getIdAsInt(), signedState.getStateHashBytes(), signature);

			// Make sure the signature set in the signed state is big enough for the current address book
			checkSigSetSize(signedState);

			signedState.getSigSet().addSigInfo(selfSigInfo);

			// The signed state will eventually be deleted, so we it will keep copy of the state we received, which
			// will immutable. The mutable copy will be the consensus state
			final State consState = signedState.getState().copy();
			platform.getSwirldStateManager().setState(consState);

			consState.getSwirldState().init(
					platform, addressBook.copy(), signedState.getState().getSwirldDualState());

			platform.getSignedStateManager().addCompleteSignedState(signedState, false);
			platform.loadIntoConsensusAndEventMapper(signedState);

			platform.getConsensusHandler().loadDataFromSignedState(signedState, true);

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

			log.debug(RECONNECT.getMarker(),
					"{} `reloadState` : reconnect complete notifications finished. Resetting fallen-behind",
					platform.getSelfId());

			platform.getSyncManager().resetFallenBehind();
			log.debug(RECONNECT.getMarker(),
					"{} `reloadState` : Resetting fallen-behind, finished",
					platform.getSelfId());
		} catch (final Exception e) {
			log.error(EXCEPTION.getMarker(), () -> new ReconnectLoadFailurePayload(
					"Error while loading a received SignedState!").toString(), e);
			// this means we need to start the reconnect process from the beginning
			log.debug(RECONNECT.getMarker(),
					"{} `reloadState` : reloading state, finished, failed, returning `false`: Restart the " +
							"reconnection process",
					platform.getSelfId());
			return false;
		}

		log.debug(RECONNECT.getMarker(),
				"{} `reloadState` : reloading state, finished, succeeded, returning `true`",
				platform.getSelfId());

		// if everything was done with no exceptions, return true
		return true;
	}

	/**
	 * Check that the size of the signature set matches the number of nodes in the address book. If it has fewer
	 * entries, create a new {@link SigSet} of the correct size and copy the existing signatures into it. This can be
	 * necessary when a new node reconnects from genesis and is provided a signed state created by the network prior to
	 * this node joining.
	 *
	 * @param signedState
	 * 		the signed state to check
	 */
	private void checkSigSetSize(final SignedState signedState) {
		final SigSet sigSet = signedState.getSigSet();
		if (sigSet.getNumMembers() < addressBook.getSize()) {
			final SigSet newSigSet = expandSigSet(sigSet);
			signedState.setSigSet(newSigSet);
		}
	}

	/**
	 * Expand the signature set to make room for this node's signature.
	 *
	 * @param sigSet
	 * 		the signature set to expand
	 * @return a new signature set with all the signatures in {@code sigSet} and this node's signature
	 */
	private SigSet expandSigSet(final SigSet sigSet) {
		final SigSet newSigSet = new SigSet(addressBook);
		for (int i = 0; i < sigSet.getNumMembers(); i++) {
			final SigInfo sigInfo = sigSet.getSigInfo(i);
			if (sigInfo != null) {
				newSigSet.addSigInfo(sigInfo);
			}
		}
		return newSigSet;
	}

}

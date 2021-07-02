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
package com.swirlds.platform;

import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.StartupTime;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.logging.payloads.ReconnectFailurePayload;
import com.swirlds.logging.payloads.ReconnectFinishPayload;
import com.swirlds.logging.payloads.ReconnectLoadFailurePayload;
import com.swirlds.logging.payloads.ReconnectStartPayload;
import com.swirlds.logging.payloads.UnableToReconnectPayload;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.internal.SystemExitReason;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.reconnect.ReconnectReceiver;
import com.swirlds.platform.state.SigInfo;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.StateDumpSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.HEARTBEAT;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.SYNC_START;

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
	/** the type of this caller */
	private final SyncCallerType callerType;

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
	 * @param callerType
	 * 		the type of this caller
	 * @param callerNumber
	 * 		0 for the first caller thread created by this platform, 1 for the next, etc
	 */
	public SyncCaller(AbstractPlatform platform, AddressBook addressBook, NodeId selfId,
			int callerNumber, SyncCallerType callerType) {
		this.platform = platform;
		this.addressBook = addressBook;
		this.selfId = selfId;
		this.callerNumber = callerNumber;
		this.callerType = callerType;
		this.failedReconnectsInARow = 0;
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
				long otherId = callRequestSync();
				if (otherId >= 0) { // successful sync
					failedAttempts = 0;
				} else {
					failedAttempts++;
					if (failedAttempts >= Settings.callerSkipsBeforeSleep) {
						failedAttempts = 0;
						try {
							// Necessary to slow down the attempts after N failures
							Thread.sleep(Settings.sleepCallerSkips);
							platform.getStats().sleep1perSecond.cycle();
						} catch (InterruptedException ex) {
							Thread.currentThread().interrupt();
							return;
						} catch (Exception ex) {
							// Suppress any exception thrown by the stats update
						}
					}
				}
			} catch (Exception e) {
				failedAttempts++;
				log.error(EXCEPTION.getMarker(), "SyncCaller.run error", e);
			}
		}
	}

	/**
	 * Chose another member at random, request a sync with them, and perform one sync if they accept the
	 * request. A -1 is returned if the sync did not happen for some reason, such as the chosen member
	 * wasn't connected, or had a communication error, or was already syncing by calling self, or the lock
	 * was still held by the heartbeat thread, or some other reason.
	 *
	 * @return member ID of the member the attempted sync was with, or -1 if no sync happened
	 * @throws ClassNotFoundException
	 * 		if bad data is received
	 */
	private int callRequestSync() {
		int otherId = -1; // the ID of the member that I am syncing with now
		SyncConnection conn = null; // connection to the member to sync with
		try { // catch any exceptions, log them, and ignore them
			/** array with a null element for each member whose signature is still needed on signed state */
			SigInfo[] sigs = null;

			boolean reconnected = false;
			if (platform.getSyncManager().hasFallenBehind()) {
				// we will not sync if we have fallen behind
				if (callerNumber == 0) {
					// caller number 0 will do the reconnect, the others will wait
					reconnected = doReconnect();
					// if reconnect failed, we will start over
					if (!reconnected) {
						return -1;
					}
					// if reconnect succeeded, we should continue with a sync
				} else {
					return -1;
				}
				reconnected = true;
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
			final List<Long> nodeList;

			if (Browser.isLoadedSavedState()) {
				// If this node loaded saved state from disk but has not created any event yet,
				// then this node is a newly added node to the network.
				// Only allow this node to sync with other pre-existing nodes, or
				// any new nodes that have finished synchronization already
				//
				// The way to tell if a node is pre-existing node or a node has finished synchronization
				// is to check whether it has any event created. EventMapper is used for this purpose
				// since it is populated with most recent event from each node after loading
				// a signed state
				nodeList = platform.getSyncManager().getNeighborsToCall(callerType).stream()
						.filter(x -> platform.getEventMapper().getMostRecentEvent(x.intValue()) != null)
						.collect(Collectors.toList());
			} else {
				nodeList = platform.getSyncManager().getNeighborsToCall(callerType);
			}

			// the array is sorted in ascending or from highest to lowest priority, so we go through the array and
			// try to
			// call each node until we are successful
			boolean syncAccepted = false;
			for (int i = 0; i < nodeList.size(); i++) {
				otherId = nodeList.get(i).intValue();
				// otherId is now the member to call

				// get the existing connection, or null if none. Do NOT try to recreate a broken connection
				conn = platform.getSyncClient().getExistingCallerConn(otherId);
				if (conn == null || !conn.connected()) {
					// if we're not yet connected, then give up before getting the 2 locks.
					// if we are connected, then we'll get the two locks, and then check the connection
					// again.
					// So the check here is just an extra check to avoid a little extra waiting, but the
					// real
					// check is below.
					continue;
				}

				log.debug(reconnected ? RECONNECT.getMarker() : SYNC_START.getMarker(),
						"{} about to call {} (connection looks good)", selfId,
						otherId);

				log.debug(reconnected ? RECONNECT.getMarker() : HEARTBEAT.getMarker(),
						"about to lock platform[{}].syncServer.lockCallListen[{}] and lockCallHeartbeat",
						platform.getSelfId(), otherId);


				final ReentrantLock lockCallListen = platform.getSyncServer().lockCallListen.get(otherId);
				final ReentrantLock lockCallHeartbeat = platform.getSyncServer().lockCallHeartbeat.get(otherId);

				// Try to get both locks. If either is unavailable, then try the next node. Never block.
				if (!lockCallListen.tryLock()) {
					continue;
				}

				log.debug(reconnected ? RECONNECT.getMarker() : HEARTBEAT.getMarker(),
						"locked platform[{}].syncServer.lockCallListen[{}]",
						platform.getSelfId(), otherId);

				try {
					// Ensure SyncCaller and heartbeat takes turns on this socket.
					// So block forever until this lock can be obtained. But it will
					// almost always be available very quickly, because only a single heartbeat thread can
					// ever
					// be holding it, and it holds it for a VERY short time, at fairly long intervals (much
					// less
					// than 1% of the time).
					if (!lockCallHeartbeat.tryLock()) {
						continue;
					}

					try {
						log.debug(HEARTBEAT.getMarker(),
								"locked platform[{}].syncServer.lockCallHeartbeat[{}]",
								platform.getSelfId(), otherId);

						// Check again in case the heartbeat thread closed the connection while waiting for
						// lock. The above check was for efficiency, but this one is the important check.
						conn = platform.getSyncClient()
								.getExistingCallerConn(otherId);
						if (conn != null && conn.connected()) {
							// try to initiate a sync. If they accept the request, then sync
							platform.getSyncServer().numSyncs.incrementAndGet(); // matching decr in finally
							try {
								final boolean ignored = false;
								syncAccepted = NodeSynchronizerImpl.synchronize(conn, true, ignored, reconnected);
								if (reconnected) {
									log.debug(RECONNECT.getMarker(),
											"`callRequestSync` : {} synced with {} ? {}",
											selfId,
											otherId,
											syncAccepted);
								}
								if (syncAccepted) {
									break;
								}

							} catch (IOException e) {
								// IOException covers both SocketTimeoutException and EOFException, plus more
								log.error(SOCKET_EXCEPTIONS.getMarker(),
										"SyncCaller.sync SocketException (so incrementing iCSyncPerSec) while {} " +
												"listening for {}:",
										platform.getSelfId(), otherId, e);

								// close the connection, don't reconnect until needed.
								conn.disconnect(true, 1);
							} catch (Exception e) {
								log.error(EXCEPTION.getMarker(),
										"! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} " +
												"listening for {}:",
										platform.getSelfId(), otherId, e);

								// close the connection, don't reconnect until needed.
								conn.disconnect(true, 2);
							} finally {
								platform.getSyncServer().numSyncs.decrementAndGet();
							}
						}
					} finally {
						lockCallHeartbeat.unlock();

						log.debug(reconnected ? RECONNECT.getMarker() : HEARTBEAT.getMarker(),
								"SyncCaller unlocked platform[{}].syncServer.lockCallHeartbeat[{}]",
								platform.getSelfId(), otherId);

					}
				} finally {
					lockCallListen.unlock();

					log.debug(reconnected ? RECONNECT.getMarker() : HEARTBEAT.getMarker(),
							"SyncCaller unlocked platform[{}].syncServer.lockCallListen[{}]",
							platform.getSelfId(), otherId);

				}
			}

			if (syncAccepted) {
				platform.getSyncManager().successfulSync();
				return otherId;
			} else {
				return -1;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();

			log.warn(EXCEPTION.getMarker(),
					"! SyncCaller.sync Interrupted (so incrementing iCSyncPerSec) while {} listening for {}:",
					platform.getSelfId(), otherId, e);

			if (conn != null) {
				conn.disconnect(true, 15);// close the connection, don't reconnect until needed.
			}

			return -1;
		} catch (Exception e) {
			log.error(EXCEPTION.getMarker(),
					"! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} listening for {}:",
					platform.getSelfId(), otherId, e);

			if (conn != null) {
				conn.disconnect(true, 14);// close the connection, don't reconnect until needed.
			}

			return -1;
		}
	}

	/**
	 * The state used to reconnect may not be hashed.
	 */
	private static void hashStateForReconnect(final State workingState) {
		try {
			CryptoFactory.getInstance().digestTreeAsync(workingState).get();
		} catch (ExecutionException e) {
			log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
					"Error encountered while hashing state for reconnect",
					ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
			throw new ReconnectException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
					"Interrupted while attempting to hash state",
					ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
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
			Browser.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
		}

		if (Settings.reconnect.getReconnectWindowSeconds() >= 0 &&
				Settings.reconnect.getReconnectWindowSeconds() < StartupTime.getTimeSinceStartup().toSeconds()) {
			log.warn(STARTUP.getMarker(), () -> new UnableToReconnectPayload(
					"Node has fallen behind, reconnect is disabled outside of time window, will die",
					platform.getSelfId().getIdAsInt()).toString());
			Browser.exitSystem(SystemExitReason.BEHIND_RECONNECT_DISABLED);
		}
	}

	/**
	 * Called to initiate the reconnect attempt by the {@link #callRequestSync()} method.
	 *
	 * @return true if the reconnect attempt completed successfully; otherwise false
	 */
	private boolean doReconnect() {
		exitIfReconnectIsDisabled();

		log.info(RECONNECT.getMarker(),
				"{} has fallen behind, will stop and clear EventFlow, Hashgraph, and ShadowGraph",
				platform.getSelfId());

		platform.getIntakeQueue().clear();

		platform.getEventMapper().clear();
		platform.getSyncShadowGraphManager().clear();

		log.info(RECONNECT.getMarker(),
				"{} has fallen behind, Hashgraph and shadow graph are now cleared",
				platform.getSelfId());

		// clear EventFlow after finish clearing RunningHashCalculator,
		// to make sure that forCons queue is empty during reconnect
		platform.getEventFlow().stopAndClear();

		SyncConnection conn;
		List<Long> reconnectNeighbors = platform.getSyncManager().getNeighborsForReconnect();
		log.info(RECONNECT.getMarker(),
				"{} has fallen behind, will try to reconnect with {}",
				platform::getSelfId, reconnectNeighbors::toString);

		final State workingState = platform.getEventFlow().getConsensusState();

		// Reserve the state
		workingState.incrementReferenceCount();

		// Hash the state if it has not yet been hashed
		hashStateForReconnect(workingState);

		try {
			for (Long neighborId : reconnectNeighbors) {
				// get the existing connection, or null if none. Do NOT try to recreate a broken connection
				conn = platform.getSyncClient().getExistingCallerConn(neighborId.intValue());
				if (conn == null || !conn.connected()) {
					// if we're not connected, try someone else
					continue;
				}
				final ReentrantLock lockCallHeartbeat = platform.getSyncServer().lockCallHeartbeat.get(
						neighborId.intValue());
				// try to get the lock, it should be available if we have fallen behind
				if (!lockCallHeartbeat.tryLock()) {
					continue;
				}

				final SignedState signedState;

				try {
					signedState = receiveStateFromNeighbor(neighborId, conn, workingState);

					if (signedState == null) {
						// The other node was unwilling to help this node to reconnect or the connection was broken
						continue;
					}
				} catch (Exception e) {
					if (Utilities.isOrCausedBySocketException(e)) {
						log.error(SOCKET_EXCEPTIONS.getMarker(), () -> new ReconnectFailurePayload(
								"Got socket exception while receiving a signed state!",
								ReconnectFailurePayload.CauseOfFailure.SOCKET).toString(), e);
					} else {
						log.error(EXCEPTION.getMarker(), () -> new ReconnectFailurePayload(
								"Error while receiving a signed state!",
								ReconnectFailurePayload.CauseOfFailure.ERROR).toString(), e);
						conn.disconnect(true, 0);
					}

					failedReconnectsInARow++;
					if (failedReconnectsInARow >= Settings.reconnect.getMaximumReconnectFailuresBeforeShutdown()) {
						log.error(EXCEPTION.getMarker(), "Too many reconnect failures in a row, killing node");
						System.exit(1);
					}

					// if we failed to receive a state from this node, we will try the next one
					continue;
				} finally {
					lockCallHeartbeat.unlock();
				}

				log.debug(RECONNECT.getMarker(), "{} `doReconnect` : finished, found peer node {}",
						platform.getSelfId(),
						conn.getOtherId());

				failedReconnectsInARow = 0;

				return reloadState(signedState);
			}

			log.debug(RECONNECT.getMarker(), "{} `doReconnect` : finished, no peer nodes found",
					platform.getSelfId());

			// if no nodes were found to reconnect with, return false
			return false;
		} finally {
			workingState.decrementReferenceCount();
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
		SignedState signedState;

		log.info(RECONNECT.getMarker(), () -> new ReconnectStartPayload(
				"Starting reconnect in role of the receiver.",
				true,
				platform.getSelfId().getIdAsInt(),
				neighborId.intValue(),
				platform.getSignedStateManager().getLastCompleteRound()).toString());

		ReconnectReceiver reconnect =
				new ReconnectReceiver(conn, addressBook, initialState, platform.getCrypto(),
						Settings.reconnect.getAsyncInputStreamTimeoutMilliseconds());

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
			final byte[] signature = platform.getCrypto().sign(signedState.getStateHashBytes());
			final SigInfo selfSigInfo = new SigInfo(signedState.getLastRoundReceived(),
					platform.getSelfId().getIdAsInt(), signedState.getStateHashBytes(), signature);
			signedState.getSigSet().addSigInfo(selfSigInfo);

			// The signed state will eventually be deleted, so we it will keep copy of the state we received, which
			// will immutable. The mutable copy will be the consensus state
			State consState = signedState.getState().copy();
			platform.getEventFlow().setState(consState);

			consState.getSwirldState().init(platform, addressBook.copy());

			platform.getSignedStateManager().addCompleteSignedState(signedState, false);
			platform.loadIntoConsensusAndEventIntake(signedState);

			platform.getEventFlow().loadDataFromSignedState(signedState, true);

			// This was added to support writing signed state JSON files after every reconnect
			// This is enabled/disabled via settings and is disabled by default
			platform.getSignedStateManager().jsonifySignedState(signedState, StateDumpSource.RECONNECT);

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
					"{} `reloadState` : reloading state, finished. Restarting threads",
					platform.getSelfId());
			platform.getEventFlow().startAll();
			log.debug(RECONNECT.getMarker(),
					"{} `reloadState` : Restarting threads, finished. Resetting fallen-behind",
					platform.getSelfId());
			platform.getSyncManager().resetFallenBehind();
			log.debug(RECONNECT.getMarker(),
					"{} `reloadState` : Resetting fallen-behind, finished",
					platform.getSelfId());
		} catch (Exception e) {
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

	/** an enum that is used to determine the type of caller */
	enum SyncCallerType {
		RANDOM, PRIORITY
	}
}

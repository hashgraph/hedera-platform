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

import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.components.EventCreationRules;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRules;
import com.swirlds.platform.components.TransThrottleSyncRule;
import com.swirlds.platform.components.TransactionTracker;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.network.RandomGraph;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import com.swirlds.platform.sync.SyncUtils;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.SYNC;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;

/**
 * A class that manages information about who we need to sync with, and whether we need to reconnect
 */
class SyncManagerImpl implements SyncManager, FallenBehindManager {

	/** the event intake queue */
	private final BlockingQueue<EventIntakeTask> intakeQueue;

	/** This object holds data on how nodes are connected to each other. */
	private final RandomGraph connectionGraph;

	/** The id of this node */
	private final NodeId selfId;

	/** This object is used for checking whether this node should create an event or not */
	private final EventCreationRules eventCreationRules;

	/** This object is used for checking whether should initiate a sync and create an event for that sync or not */
	private final TransThrottleSyncAndCreateRules transThrottleSyncAndCreateRules;

	/** A list of rules for checking if this not should sync with other nodes */
	private final List<TransThrottleSyncRule> transThrottleSyncRules;

	/** Returns the round number of the latest round saved to disk. */
	private final Supplier<Long> lastRoundSavedToDisk;

	/** Returns the round number of the latest round where we have a super-majority. */
	private final Supplier<Long> lastCompletedRound;

	/** Notifies the platform that its status may have changed. */
	private final Runnable notifyPlatform;

	/** Tracks user transactions in the hashgraph */
	private final TransactionTracker transactionTracker;

	/** Tracks recent events */
	private final CriticalQuorum criticalQuorum;

	/** The initial address book */
	private final AddressBook addressBook;

	/** a set of all neighbors of this node */
	private final HashSet<Long> allNeighbors;
	/** the number of neighbors we have */
	private final int numNeighbors;

	/** number of neighbors who think this node has fallen behind */
	volatile int numReportFallenBehind = 0;
	/** set of neighbors who report that this node has fallen behind */
	private final HashSet<Long> reportFallenBehind;
	/**
	 * set of neighbors that have not yet reported that we have fallen behind, only exists if someone reports we have
	 * fallen behind. This Set is made from a ConcurrentHashMap, so it needs no synchronization
	 */
	private final Set<Long> notYetReportFallenBehind;

	/**
	 * initial value of transThrottleInitialCalls
	 */
	private static final int TRANS_THROTTLE_INITIAL_CALLS_NUM = 10;

	/** a counter that turns off transThrottle initially until it reaches 0 */
	private final AtomicInteger transThrottleInitialCalls = new AtomicInteger(TRANS_THROTTLE_INITIAL_CALLS_NUM);
	/**
	 * a counter that keeps track of the number of consecutive syncs that happened without any events being
	 * transferred
	 */
	private final AtomicInteger transThrottleSyncsWithNoEvents = new AtomicInteger(0);

	/**
	 * When looking for a neighbor to call, this is the maximum number of neighbors we query before just selecting
	 * one if a suitable neighbor is not found yet.
	 */
	private static final int MAXIMUM_NEIGHBORS_TO_QUERY = 10;

	/**
	 * Creates a new SyncManager
	 *
	 * @param intakeQueue
	 * 		the event intake queue
	 * @param connectionGraph
	 * 		The platforms connection graph.
	 * @param selfId
	 * 		The ID of the platform.
	 * @param eventCreationRules
	 * 		Contains a list of rules for checking whether this node should create an event or not
	 * @param transThrottleSyncRules
	 * 		A list of rules for checking if this not should sync with other nodes
	 * @param transThrottleSyncAndCreateRules
	 * 		Contains a list of rules for checking whether should initiate a sync and create an event for that sync or
	 * 		not
	 * @param lastRoundSavedToDisk
	 * 		A binding of signedStateMgr.getLastRoundSavedToDisk().
	 * @param lastCompletedRound
	 * 		A binding of signedStateMgr.getLastCompleteRound()).
	 * @param transactionTracker
	 * 		tracks user transactions within events
	 */
	SyncManagerImpl(
			final BlockingQueue<EventIntakeTask> intakeQueue,
			final RandomGraph connectionGraph,
			final NodeId selfId,
			final EventCreationRules eventCreationRules,
			final List<TransThrottleSyncRule> transThrottleSyncRules,
			final TransThrottleSyncAndCreateRules transThrottleSyncAndCreateRules,
			final Supplier<Long> lastRoundSavedToDisk,
			final Supplier<Long> lastCompletedRound,
			final Runnable notifyPlatform,
			final TransactionTracker transactionTracker,
			final CriticalQuorum criticalQuorum,
			final AddressBook addressBook) {
		super();

		this.intakeQueue = intakeQueue;
		this.connectionGraph = connectionGraph;
		this.selfId = selfId;

		this.eventCreationRules = eventCreationRules;
		this.transThrottleSyncRules = transThrottleSyncRules;
		this.transThrottleSyncAndCreateRules = transThrottleSyncAndCreateRules;

		this.lastRoundSavedToDisk = lastRoundSavedToDisk;
		this.lastCompletedRound = lastCompletedRound;
		this.notifyPlatform = notifyPlatform;
		this.transactionTracker = transactionTracker;
		this.criticalQuorum = criticalQuorum;
		this.addressBook = addressBook;

		notYetReportFallenBehind = ConcurrentHashMap.newKeySet();
		reportFallenBehind = new HashSet<>();
		allNeighbors = new HashSet<>();
		/* an array with all the neighbor ids */
		int[] neighbors = this.connectionGraph.getNeighbors(this.selfId.getIdAsInt());
		numNeighbors = neighbors.length;
		for (final int neighbor : neighbors) {
			allNeighbors.add((long) neighbor);
		}
	}

	/**
	 * A method called by the sync listener to determine whether a sync should be accepted or not
	 *
	 * @return true if the sync should be accepted, false otherwise
	 */
	@Override
	public boolean shouldAcceptSync() {
		// we shouldn't sync if the event intake queue is too big
		final int intakeQueueSize = intakeQueue.size();
		if (intakeQueueSize > Settings.eventIntakeQueueThrottleSize) {
			LogManager.getLogger().debug(SYNC.getMarker(),
					"don't accept sync because event intake queue is too big, size: {}",
					intakeQueueSize);
			return false;
		}
		return true;
	}

	/**
	 * A method called by the sync caller to determine whether a sync should be initiated or not
	 *
	 * @return true if the sync should be initiated, false otherwise
	 */
	@Override
	public boolean shouldInitiateSync() {
		//we shouldn't sync if the event intake queue is too big
		return intakeQueue.size() <= Settings.eventIntakeQueueThrottleSize;
	}

	/**
	 * Retrieves a list of neighbors in order of syncing priority
	 *
	 * @return a list of neighbors
	 */
	@Override
	public List<Long> getNeighborsToCall() {
		// if there is an indication we might have fallen behind, calling nodes to establish this takes priority
		List<Long> list = getNeededForFallenBehind();
		if (list != null) {
			return list;
		}
		list = new LinkedList<>();
		for (int i = 0; i < MAXIMUM_NEIGHBORS_TO_QUERY; i++) {
			final long neighbor = connectionGraph.randomNeighbor(selfId.getIdAsInt());

			// don't add duplicated nodes here
			if (list.contains(neighbor)) {
				continue;
			}

			// we try to call a neighbor in the bottom 1/3 by number of events created in the latest round, if
			// we fail to find one after 10 tries, we just call the last neighbor we find
			if (criticalQuorum.isInCriticalQuorum(neighbor) || i == MAXIMUM_NEIGHBORS_TO_QUERY - 1) {
				list.add(neighbor);
			}
		}

		return list;
	}

	/**
	 * Ask the SyncManager whether we should stop calling other nodes because there are no more user transactions
	 *
	 * @return true if the caller should initiate a sync, false otherwise
	 */
	@Override
	public boolean transThrottle() {
		// check 1: first consider all the checks in transThrottleCallAndCreate
		if (transThrottleCallAndCreate()) {
			return true;
		}
		// check 2: should be off while event creation is frozen, this includes the startup freeze
		for (final TransThrottleSyncRule syncRule : transThrottleSyncRules) {
			if (syncRule.shouldSync()) {
				return true;
			}
		}

		// check 3: did we have enough syncs with not events transferred, if not, we should sync
		if (transThrottleSyncsWithNoEvents.get() < 10) {
			return true;
		}
		// if all checks have passed, then we don't need to call anyone until one of the conditions changes
		return false;
	}

	/**
	 * Checks if according to transThrottle, we should initiate a sync and create an event for that sync
	 *
	 * @return true if we should sync and create an event
	 */
	@Override
	public boolean transThrottleCallAndCreate() {
		// check 1: if transThrottle is off, initiate a sync and create an event
		if (!Settings.transThrottle) {
			return true;
		}
		// check 2: when a node starts up or does a reconnect,
		// don't stop calling until transThrottleInitialCalls reaches zero
		if (transThrottleInitialCalls.get() > 0) {
			return true;
		}
		// check 3: if we have non consensus user transaction in the hashgraph, initiate a sync. This is the most common
		// scenario. We will only check the saved states if there are no non consensus transactions in the hashgraph.
		if (transactionTracker.getNumUserTransEvents() > 0) {
			return true;
		}

		final long roundReceivedAllCons = transactionTracker.getLastRoundReceivedAllTransCons();
		if (Settings.state.getSaveStatePeriod() > 0) {
			// check 4: if we are saving states to disk, then we need to sync until we have saved a state that has
			// processed all transactions
			if (roundReceivedAllCons > lastRoundSavedToDisk.get()) {
				return true;
			}
		} else {
			// check 5: if we are not saving states to disk, then we only need to collect enough signatures for the
			// state that has all the transactions handled
			if (roundReceivedAllCons > lastCompletedRound.get()) {
				return true;
			}
		}

		// check 6: if we have transactions waiting to be put into an event, initiate a sync
		// check 7: transThrottle should be off 1 minute before and during the freeze period
		// check 8: waitAtStartup, don't throttle until all nodes have started
		// if all checks have passed, then we don't need to call anyone until one of the conditions changes
		return transThrottleSyncAndCreateRules.shouldSyncAndCreate() == SYNC_AND_CREATE;
	}

	/**
	 * Called by SyncUtils after a successful sync to check whether it should create an event or not
	 *
	 * @param otherId
	 * 		the ID of the node we synced with
	 * @param oneNodeFallenBehind
	 * 		true if one of the nodes in the sync has fallen behind
	 * @param eventsRead
	 * 		the number of events read during the sync
	 * @param eventsWritten
	 * 		the number of events written during the sync
	 * @return true if an event should be created, false otherwise
	 */
	@Override
	public boolean shouldCreateEvent(
			final NodeId otherId, final boolean oneNodeFallenBehind, final int eventsRead, final int eventsWritten) {
		return shouldCreateEvent(
				new SyncResult(/*unused here*/false, otherId, eventsRead, eventsWritten));
	}

	/**
	 * Called by {@link SyncUtils} after a successful sync to check whether it should create an event or not
	 *
	 * @param info
	 * 		information about the sync
	 * @return true if an event should be created, false otherwise
	 */
	@Override
	public boolean shouldCreateEvent(final SyncResult info) {
		// check EventCreationRules:
		// (1) if the node is not main node, it should not create events.
		// (2) if the number of freeze transactions is greater than 0, should create an event.
		// (3) during startup freeze, should not create an event.
		// (4) in freeze period, should not create an event.
		final EventCreationRuleResponse response = eventCreationRules.shouldCreateEvent();
		if (response == EventCreationRuleResponse.CREATE) {
			return true;
		} else if (response == EventCreationRuleResponse.DONT_CREATE) {
			return false;
		}

		// check 3: if neither node is part of the superMinority in the latest round, don't create an event
		if (!criticalQuorum.isInCriticalQuorum(info.getOtherId().getId()) &&
				!criticalQuorum.isInCriticalQuorum(selfId.getId())) {
			return false;
		}
		if (info.getEventsRead() + info.getEventsWritten() > 0) {
			// if we have transferred events, reset the counter
			transThrottleSyncsWithNoEvents.set(0);
		}
		// check 4: transThrottle
		if (!transThrottleCallAndCreate()) {
			// once transThrottle says we should not create any more events, we should start keeping track of the
			// transThrottleSyncsWithNoEvents counter
			if (info.getEventsRead() + info.getEventsWritten() == 0) {
				// if no events were transferred, we should increment the counter
				transThrottleSyncsWithNoEvents.incrementAndGet();
			}
			return false;
		}

		// check 5: staleEventPrevention
		if (Settings.staleEventPreventionThreshold > 0 &&
				info.getEventsRead() > Settings.staleEventPreventionThreshold * addressBook.getSize()) {
			// if we read too many events during this sync, we skip creating an event to reduce the probability of
			// having a stale event
			return false;
		}


		// if all checks pass, an event should be created
		return true;
	}

	/**
	 * Notifies the sync manager that there was a successful sync
	 */
	@Override
	public void successfulSync() {
		if (transThrottleInitialCalls.get() > 0) {
			// since this is not synchronized, it might make the transThrottleInitialCalls go below 0, but that isn't an
			// issue and will only result in a few extra initial syncs
			transThrottleInitialCalls.decrementAndGet();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void reportFallenBehind(final NodeId id) {
		if (reportFallenBehind.add(id.getId())) {
			if (numReportFallenBehind == 0) {
				// we have received the first indication that we have fallen behind, so we need to check with other
				// nodes to confirm
				notYetReportFallenBehind.addAll(allNeighbors);
			}
			// we don't need to check with this node
			notYetReportFallenBehind.remove(id.getId());
			numReportFallenBehind++;
			if (hasFallenBehind()) {
				notifyPlatform.run();
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void resetFallenBehind() {
		numReportFallenBehind = 0;
		reportFallenBehind.clear();
		notYetReportFallenBehind.clear();
		transThrottleInitialCalls.set(TRANS_THROTTLE_INITIAL_CALLS_NUM);
		notifyPlatform.run();
	}

	/**
	 * Returns a list of node IDs which need to be contacted to establish if we have fallen behind.
	 *
	 * @return a list of node IDs, or null if there is no indication we have fallen behind
	 */
	protected List<Long> getNeededForFallenBehind() {
		if (notYetReportFallenBehind.size() == 0) {
			return null;
		}
		final List<Long> ret = new ArrayList<>(notYetReportFallenBehind);
		Collections.shuffle(ret);
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasFallenBehind() {
		return numNeighbors * Settings.reconnect.getFallenBehindThreshold() < numReportFallenBehind;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<Long> getNeighborsForReconnect() {
		final List<Long> ret = new ArrayList<>(reportFallenBehind);
		Collections.shuffle(ret);
		return ret;
	}
}

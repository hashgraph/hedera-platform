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

import com.swirlds.common.NodeId;
import org.apache.logging.log4j.LogManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.swirlds.logging.LogMarker.SYNC;

/**
 * A class that manages information about who we need to sync with, and whether we need to reconnect
 */
class SyncManager {

	/** The hashgraph used by the platform to perform consensus operations. */
	private final AbstractHashgraph hashgraph;

	/** The platform uses this object to manage swirlds state and various queues of events. */
	private final AbstractEventFlow eventFlow;

	/** This object holds data on how nodes are connected to each other. */
	private final RandomGraph connectionGraph;

	/** The id of this node */
	private final NodeId selfId;

	/** Checks to see if event creation is frozen for something such as re-connection. */
	private final Supplier<Boolean> isEventCreationFrozen;

	/** Returns the number of threads currently in a sync. */
	private final Supplier<Integer> numListenerSyncs;

	/** Returns the round number of the latest round saved to disk. */
	private final Supplier<Long> lastRoundSavedToDisk;

	/** Returns the round number of the latest round where we have a super-majority. */
	private final Supplier<Long> lastCompletedRound;

	/** Notifies the platform that its status may have changed. */
	private final Runnable notifyPlatform;

	/** a set of all neighbors of this node */
	private final HashSet<Long> allNeighbors;
	/** the number of neighbors we have */
	private final int numNeighbors;
	/** an array with all the neighbor ids */
	private int[] neighbors;

	/** number of neighbors who think this node has fallen behind */
	volatile int numReportFallenBehind = 0;
	/** set of neighbors who report that this node has fallen behind */
	private final HashSet<Long> reportFallenBehind;
	/**
	 * set of neighbors that have not yet reported that we have fallen behind, only exists if someone reports we have
	 * fallen behind. This Set is made from a ConcurrentHashMap, so it needs no synchronization
	 */
	private final Set<Long> notYetReportFallenBehind;

	/** a counter that turns off transThrottle initially until it reaches 0 */
	private final AtomicInteger transThrottleInitalCalls = new AtomicInteger(10);
	/**
	 * a counter that keeps track of the number of consecutive syncs that happened without any events being
	 * transferred
	 */
	private final AtomicInteger transThrottleSyncsWithNoEvents = new AtomicInteger(0);

	/**
	 * Creates a new SyncManager
	 *
	 * @param hashgraph
	 * 		The Hashgraph used by the platform.
	 * @param eventFlow
	 * 		The EventFlow used by the platform.
	 * @param connectionGraph
	 * 		The platforms connection graph.
	 * @param selfId
	 * 		The ID of the platform.
	 * @param isEventCreationFrozen
	 * 		A binding of freezeManager.isEventCreationFrozen().
	 * @param numListenerSyncs
	 * 		A binding of syncServer.numListenerSyncs.get().
	 * @param lastRoundSavedToDisk
	 * 		A binding of signedStateMgr.getLastRoundSavedToDisk().
	 * @param lastCompletedRound
	 * 		A binding of signedStateMgr.getLastCompleteRound()).
	 */
	SyncManager(
			AbstractHashgraph hashgraph,
			AbstractEventFlow eventFlow,
			RandomGraph connectionGraph,
			NodeId selfId,
			Supplier<Boolean> isEventCreationFrozen,
			Supplier<Integer> numListenerSyncs,
			Supplier<Long> lastRoundSavedToDisk,
			Supplier<Long> lastCompletedRound,
			Runnable notifyPlatform) {
		super();

		this.hashgraph = hashgraph;
		this.eventFlow = eventFlow;
		this.connectionGraph = connectionGraph;
		this.selfId = selfId;
		this.isEventCreationFrozen = isEventCreationFrozen;
		this.numListenerSyncs = numListenerSyncs;
		this.lastRoundSavedToDisk = lastRoundSavedToDisk;
		this.lastCompletedRound = lastCompletedRound;
		this.notifyPlatform = notifyPlatform;

		notYetReportFallenBehind = ConcurrentHashMap.newKeySet();
		reportFallenBehind = new HashSet<>();
		allNeighbors = new HashSet<>();
		neighbors = this.connectionGraph.getNeighbors(this.selfId.getIdAsInt());
		numNeighbors = neighbors.length;
		for (int i = 0; i < neighbors.length; i++) {
			allNeighbors.add((long) neighbors[i]);
		}
	}

	/**
	 * A method called by the sync listener to determine whether a sync should be accepted or not
	 *
	 * @return true if the sync should be accepted, false otherwise
	 */
	boolean shouldAcceptSync() {
		// we shouldn't sync if the event intake queue is too big
		final int intakeQueueSize = hashgraph.getEventIntakeQueueSize();
		if (intakeQueueSize > Settings.eventIntakeQueueThrottleSize) {
			LogManager.getLogger().debug(SYNC.getMarker(),
					"don't accept sync because event intake queue is too big, size: {}",
					intakeQueueSize);
			return false;
		}

		final int numListenerSyncsValue = numListenerSyncs.get();
		if (numListenerSyncsValue > Settings.maxIncomingSyncsInc + Settings.maxOutgoingSyncs) {
			LogManager.getLogger().debug(SYNC.getMarker(),
					"don't accept sync because numListenerSyncs can't exceed limit, size: {}",
					numListenerSyncsValue);
			return false;
		}
		return true;
	}

	/**
	 * A method called by the sync caller to determine whether a sync should be initiated or not
	 *
	 * @return true if the sync should be initiated, false otherwise
	 */
	boolean shouldInitiateSync() {
		//we shouldn't sync if the event intake queue is too big
		if (hashgraph.getEventIntakeQueueSize() > Settings.eventIntakeQueueThrottleSize) {
			return false;
		}
		return true;
	}

	/**
	 * Retrieves a list of neighbors in order of syncing priority
	 *
	 * @param callerType
	 * 		the type of caller to create a list for
	 * @return a list of neighbors
	 */
	List<Long> getNeighborsToCall(SyncCaller.SyncCallerType callerType) {
		// if there is an indication we might have fallen behind, calling nodes to establish this takes priority
		List<Long> list = getNeededForFallenBehind();
		if (list != null) {
			return list;
		}
		switch (callerType) {
			case RANDOM:
			case PRIORITY:
				list = new LinkedList<>();
				for (int i = 0; i < 10; i++) {
					long neighbor = connectionGraph.randomNeighbor(selfId.getIdAsInt());
					// we try to call a neighbor in the bottom 1/3 by number of events created in the latest round, if
					// we fail to find one after 10 tries, we just call the last neighbor we find
					if (hashgraph.isStrongMinorityInMaxRound(neighbor) || i == 9) {
						list.add(neighbor);
						return list;
					}
				}

				return list;
		}
		return null;
	}

	/**
	 * Ask the SyncManager whether we should stop calling other nodes because there are no more user transactions
	 *
	 * @return true if the caller should initiate a sync, false otherwise
	 */
	boolean transThrottle() {
		// check 1: first consider all the checks in transThrottleCallAndCreate
		if (transThrottleCallAndCreate()) {
			return true;
		}
		// check 2: should be off while event creation is frozen, this includes the startup freeze
		if (isEventCreationFrozen.get()) {
			return true;
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
	boolean transThrottleCallAndCreate() {
		// check 1: if transThrottle is off, initiate a sync and create an event
		if (!Settings.transThrottle) {
			return true;
		}
		// check 2: when a node starts up or does a reconnect, don't stop calling until transThrottleInitalCalls reaches
		// zero
		if (transThrottleInitalCalls.get() > 0) {
			return true;
		}
		// check 3: if we have non consensus user transaction in the hashgraph, initiate a sync. This is the most common
		// scenario. We will only check the saved states if there are no non consensus transactions in the hashgraph.
		if (hashgraph.getNumUserTransEvents() > 0) {
			return true;
		}
		// check 4: if we have transactions waiting to be put into an event, initiate a sync
		if (eventFlow.numUserTransForEvent() > 0) {
			return true;
		}
		long roundReceivedAllCons = hashgraph.getLastRoundReceivedAllTransCons();
		if (Settings.state.getSaveStatePeriod() > 0) {
			// check 5: if we are saving states to disk, then we need to sync until we have saved a state that has
			// processed all transactions
			if (roundReceivedAllCons > lastRoundSavedToDisk.get()) {
				return true;
			}
		} else {
			// check 6: if we are not saving states to disk, then we only need to collect enough signatures for the
			// state that has all the transactions handled
			if (roundReceivedAllCons > lastCompletedRound.get()) {
				return true;
			}
		}
		// check 7: tranThrottle should be off 1 minute before and during the freeze period
		if (FreezeManager.isInFreezePeriod(Instant.now().plusMillis(60 * 1000)) ||
				FreezeManager.isInFreezePeriod(Instant.now())) {
			return true;
		}
		// check 8: waitAtStartup, don't throttle until all nodes have started
		if (Settings.waitAtStartup && hashgraph.getNumNotStarted() > 0) {
			return true;
		}
		// if all checks have passed, then we don't need to call anyone until one of the conditions changes
		return false;
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
	boolean shouldCreateEvent(NodeId otherId, boolean oneNodeFallenBehind, int eventsRead, int eventsWritten) {

		// Only main nodes should create events
		if (!selfId.isMain()) {
			return false;
		}

		if (eventFlow.numFreezeTransEvent() > 0) {
			return true;
		}

		// check 1: if event creation is frozen, dont create an event
		if (isEventCreationFrozen.get()) {
			return false;
		}
		// check 2: if one node had fallen behind, don't create an event
		if (oneNodeFallenBehind) {
			return false;
		}
		// check 3: if neither node is part of the superMinority in the latest round, don't create an event
		if (!hashgraph.isStrongMinorityInMaxRound(otherId.getId()) &&
				!hashgraph.isStrongMinorityInMaxRound(selfId.getId())) {
			return false;
		}
		// check 4: waitAtStartup
		// If waitAtStartup is active, then there are some cases when it
		// shouldn't create the event. This member should only create a single event
		// prior to receiving at least one event from every member. And that event
		// should only be created after receiving an event from member 0 (except that
		// member 0 can create the event before receiving from self).
		if (Settings.waitAtStartup && hashgraph.getNumNotStarted() > 0) {
			// check 3.1: if I have created an event, but someone else hasn't, don't create anymore events yet
			if (hashgraph.hasNodeStarted(selfId.getIdAsInt())) {
				return false;
			}
			// check 3.2: if I am not node 0, I should wait for node 0 to create an event first
			if (selfId.getId() != 0 && !hashgraph.hasNodeStarted(0)) {
				return false;
			}
		}
		if (eventsRead + eventsWritten > 0) {
			// if we have transferred events, reset the counter
			transThrottleSyncsWithNoEvents.set(0);
		}
		// check 4: transThrottle
		if (!transThrottleCallAndCreate()) {
			// once transThrottle says we should not create any more events, we should start keeping track of the
			// transThrottleSyncsWithNoEvents counter
			if (eventsRead + eventsWritten == 0) {
				// if no events were trasferred, we should increment the counter
				transThrottleSyncsWithNoEvents.incrementAndGet();
			}
			return false;
		}

		// check 5: staleEventPrevention
		if (Settings.staleEventPreventionThreshold > 0 &&
				eventsRead > Settings.staleEventPreventionThreshold * hashgraph.getAddressBook().getSize()) {
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
	void successfulSync() {
		if (transThrottleInitalCalls.get() > 0) {
			// since this is not synchronized, it might make the transThrottleInitalCalls go below 0, but that isn't an
			// issue and will only result in a few extra initial syncs
			transThrottleInitalCalls.decrementAndGet();
		}
	}

	/**
	 * Notify the sync manager that a node has reported that they don't have events we need. This means we have probably
	 * fallen behind and will need to reconnect
	 *
	 * @param id
	 * 		the id of the node who says we have fallen behind
	 */
	synchronized void reportFallenBehind(NodeId id) {
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
	 * We have determined that we have not fallen behind, or we have reconnected, so reset everything to the initial
	 * state
	 */
	synchronized void resetFallenBehind() {
		numReportFallenBehind = 0;
		reportFallenBehind.clear();
		notYetReportFallenBehind.clear();
		transThrottleInitalCalls.set(10);
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
		List<Long> ret = new ArrayList<>(notYetReportFallenBehind);
		Collections.shuffle(ret);
		return ret;
	}

	/**
	 * Have enough nodes reported that they don't have events we need, and that we have fallen behind?
	 *
	 * @return true if we have fallen behind, false otherwise
	 */
	boolean hasFallenBehind() {
		return numNeighbors * Settings.reconnect.getFallenBehindThreshold() < numReportFallenBehind;
	}

	/**
	 * Get a list of neighbors to call if we need to do a reconnect
	 *
	 * @return a list of neighbor IDs
	 */
	synchronized List<Long> getNeighborsForReconnect() {
		List<Long> ret = new ArrayList<>(reportFallenBehind);
		Collections.shuffle(ret);
		return ret;
	}
}

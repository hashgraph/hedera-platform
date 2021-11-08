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
package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.Event;
import com.swirlds.platform.EventImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.swirlds.logging.LogMarker.EXPIRE_EVENT;
import static com.swirlds.logging.LogMarker.RECONNECT;

/**
 * <p>At a high level, this type coordinates hashgraph/consensus on this node and other-nodes. </p>
 * This type aggregates an implementation of `ShadowGraph`. It supports the following behaviors.
 * <ul>
 * <li>
 * Insertion of shadow events.
 * Every added event is a new tip event, replacing its self-parent as a tip.
 * </li>
 * <li>
 * Expiration of shadow events.
 * Shadow events are expired by generation. When the expired gen increases, the SGM deletes all shadow events with
 * generation less than or equal to expired generation
 * </li>
 * <li>
 * Querying the set of tip events for the shadow graph.
 * </li>
 * <li>
 * Querying for a shadow event by base hash of a hashgraph event
 * </li>
 * <li>
 * Traversing sub-DAGs from a given shadow graph vertex.
 * </li>
 * </ul>
 * An SGM is executed on multiple, concurrent threads.
 *
 * The SGM may add an event during a traversal.
 * It may expire an event (and its ancestors) during a traversal.
 *
 * An SGM manages synchronization between traversal and event insertion/expiration.
 *
 * This implementation therefore constitutes two interfaces: one for event intake, and one for
 * gossip.
 */
public final class ShadowGraphManager {
	private static final Logger LOG = LogManager.getLogger();

	// The shadow graph
	public final ShadowGraph shadowGraph;

	// The set of all tips for the shadow graph. A tip is an event with no self-child (could have other-child)
	private final HashSet<ShadowEvent> tips;

	//the latest generation that is expired
	private long expiredGen;

	/**
	 * Production constructor.
	 *
	 * Default-construct a shadow graph manager. Used only by SwirldsPlatform
	 */
	public ShadowGraphManager() {
		this.shadowGraph = new ShadowGraph();
		this.expiredGen = Long.MIN_VALUE;

		this.tips = new HashSet<>();
	}

	/**
	 * Test constructor.
	 *
	 * Construct a shadow graph manager instance for a given shadow graph. Used only for testing.
	 *
	 * @param shadowGraph
	 * 		The shadow graph for which mutating operations are to be executed
	 */
	public ShadowGraphManager(final ShadowGraph shadowGraph) {
		this(shadowGraph, -1);
	}

	/**
	 * Test constructor.
	 *
	 * Construct a shadow graph manager instance for a given shadow graph, with a given most recent expired generation.
	 * Used only for testing.
	 *
	 * @param shadowGraph
	 * 		The shadow graph for which mutating operations are to be executed
	 * @param expiredGen
	 * 		The initial value of the most recent expired generation to be used
	 */
	public ShadowGraphManager(final ShadowGraph shadowGraph, final long expiredGen) {
		this.shadowGraph = shadowGraph;
		this.expiredGen = expiredGen;

		this.tips = new HashSet<>();
		identifyTips();
	}

	/**
	 * Reset the shadow graph to its constructed state. Remove all tips and shadow events, and reset the expired
	 * generation to {@link Long#MIN_VALUE}.
	 */
	public void clear() {
		shadowGraph.clear();
		tips.clear();
		expiredGen = Long.MIN_VALUE;
	}

	/**
	 * Sets the working tips and the sending tips in syncData
	 *
	 * @param syncData
	 * 		the object to modify
	 */
	public synchronized void setInitialTips(final SyncData syncData) {
		syncData.getSendingTipList().clear();
		syncData.getSendingTipList().addAll(this.tips);
		syncData.getSendingTipList().sort(Comparator.comparingLong(s0 -> s0.getEvent().getCreatorId()));
		syncData.getWorkingTips().clear();
		syncData.getWorkingTips().addAll(syncData.getSendingTipList());
		syncData.getSendingTips().clear();

		for (final ShadowEvent tip : syncData.getSendingTipList()) {
			syncData.getSendingTips().add(tip.getEventBaseHash());
		}

		syncData.setMaxTipGenerations(syncData.getSendingTipList());
	}

	/**
	 * Phase 1 (receive): Apply a list of tip hashes received from another node to this shadow graph, with optional
	 * logging. This begins the identification of Hashgraph events to send to the peer.
	 *
	 * @param syncData
	 * 		The instance that holds the received tip hashes.
	 * @param syncLogString
	 * 		The log string which identifies the local node and remote node for the current connection, and
	 * 		whether this node is the caller or listener.
	 */
	public synchronized void setReceivedTipHashes(final SyncData syncData, final String syncLogString) {
		setReceivedTipHashes(syncData);
	}

	/**
	 * Phase 1 (receive): Apply a list of tip hashes received from another node to this shadow graph. This begins the
	 * identification of Hashgraph events to send to the peer.
	 *
	 * @param syncData
	 * 		The instance that holds the received tip hashes.
	 */
	public synchronized void setReceivedTipHashes(final SyncData syncData) {

		final HashSet<ShadowEvent> visited = new HashSet<>();

		syncData.getReceivedTipHashes().forEach((Hash h) -> {
			final ShadowEvent receivedTip = shadowGraph.shadow(h);
			if (receivedTip != null) {
				syncData.markForSync(receivedTip);
				syncData.getWorkingTips().remove(receivedTip);

				for (final ShadowEvent sendingTip : syncData.getSendingTipList()) {
					if (sendingTip.getEvent().getCreatorId() == receivedTip.getEvent().getCreatorId()
							&& sendingTip.getEvent().getGeneration() > receivedTip.getEvent().getGeneration()) {
						processStrictSelfDescendants(
								syncData.getSendList(),
								syncData.getWorkingTips(),
								syncData.getSendingTips(),
								syncData.getMaxTipGenerations(),
								syncData.getMaxTipGeneration(),
								visited,
								receivedTip);
					}
				}
			}
		});
	}


	/**
	 * Phase 2 (send): Get a list of booleans for the tips received in Phase 1.
	 *
	 * @param syncData
	 * 		The instance that holds the tip hashes received in Phase 1
	 * @return A list of booleans, exactly one boolean per received tip
	 */
	public synchronized List<Boolean> getSendTipBooleans(final SyncData syncData) {
		final List<Hash> receivedTipHashes = syncData.getReceivedTipHashes();
		return getSendTipBooleans(receivedTipHashes);
	}


	/**
	 * Get the list of tip booleans for this node to send to the peer. Used in phase 2.
	 *
	 * @param receivedTipHashes
	 * 		tip hashes received from the peer node
	 * @return the list of tip booleans for this node
	 */
	public synchronized List<Boolean> getSendTipBooleans(final List<Hash> receivedTipHashes) {
		final List<Boolean> sendFlags = new ArrayList<>();

		for (int i = 0; i < receivedTipHashes.size(); ++i) {
			final ShadowEvent receivedTip = shadowGraph.shadow(receivedTipHashes.get(i));
			if (receivedTip != null && receivedTip.getNumSelfChildren() > 0) {
				sendFlags.add(true);
			} else {
				sendFlags.add(false);
			}
		}

		return sendFlags;
	}

	/**
	 * Phase 2 (receive): Apply a list of tip booleans received from a communicating peer.
	 *
	 * @param syncData
	 * 		The instance that holds the tip mark fields set by this routine.
	 * @param receivedTipBooleans
	 * 		The tip received in from the peer.
	 */
	public synchronized void setReceivedTipBooleans(final SyncData syncData, final List<Boolean> receivedTipBooleans) {
		final List<Hash> tipHashes = syncData.getSendingTipHashes();

		for (int i = 0; i < receivedTipBooleans.size(); ++i) {
			final boolean b = receivedTipBooleans.get(i);
			if (b) {
				syncData.markForSync(tipHashes.get(i));
				syncData.getWorkingTips().remove(shadowGraph.shadow(tipHashes.get(i)));
			}
		}
	}

	/**
	 * Phase 3 (send): Get a list of Hashgraph events to send to the peer, with optional logging. This finishes the
	 * identification of Hashgraph events to send to the peer.
	 *
	 * @param syncData
	 * 		The instance that holds the tip mark fields set by this routine.
	 * @param syncLogString
	 * 		The log string which identifies the local node and remote node for the current connection, and
	 * 		whether this node is the caller or listener.
	 * @return A list of Hashgraph events to send to the communicating peer.
	 */
	public synchronized List<EventImpl> finishSendEventList(final SyncData syncData, final String syncLogString) {
		final List<EventImpl> sendList = syncData.getSendList();

		finishSendEventList(syncData);

		return sendList;
	}


	/**
	 * Phase 3 (send): Get a list of Hashgraph events to send to the peer. This finishes the identification of Hashgraph
	 * events to send to the peer.
	 *
	 * @param syncData
	 * 		the instance that holds the tip mark fields set by this routine.
	 */
	public synchronized void finishSendEventList(final SyncData syncData) {

		final Set<ShadowEvent> workingTips = syncData.getWorkingTips();
		final List<EventImpl> sendList = syncData.getSendList();
		final Set<EventImpl> sendSet = new HashSet<>();

		// Linear traversal: Remember visited events across multiple instantiations of the
		// graph iterator in the internal for-loop below. Previously visited events will not be re-visited
		// from parents. They may be re-visited from children.
		final Set<ShadowEvent> visited = new HashSet<>();

		for (final ShadowEvent workingTip : workingTips) {
			syncData.notifyTipAbsorptionActivated();
			ShadowEvent ancestor = workingTip;

			while (ancestor != null) {

				for (final ShadowEvent descendant : shadowGraph.graphDescendants(ancestor, syncData.getSendingTips(),
						visited,
						syncData.getMaxTipGenerations(), true)) {

					if (syncData.markedForSync(descendant)) {
						syncData.markForSearch(ancestor);
						break;
					}
				}

				if (!syncData.markedForSearch(ancestor)) {
					sendSet.add((EventImpl) ancestor.getEvent());
				} else {
					break;
				}

				ancestor = ancestor.getSelfParent();
			}
		}

		sendList.addAll(sendSet);

		sort(sendList);
	}

	/**
	 * Predicate to determine if an event has expired.
	 *
	 * @param event
	 * 		The event.
	 * @return true iff the given event is expired
	 */
	public synchronized boolean expired(final Event event) {
		return event.getGeneration() <= expiredGen;
	}

	/**
	 * Remove events that have expired
	 *
	 * @param newExpiredGeneration
	 * 		Any event with a generation less than or equal to `newExpiredGeneration` is to be removed.
	 * @return number of events expunged from the shadow graph during this function call
	 */
	public synchronized int expire(final long newExpiredGeneration) {
		LOG.debug(EXPIRE_EVENT.getMarker(),
				"SG newExpiredGeneration {}", newExpiredGeneration);
		if (newExpiredGeneration == expiredGen) {
			return 0;
		}

		setExpiredGeneration(newExpiredGeneration);
		return expire();
	}

	/**
	 * Remove events that have expired. Any event with a generation less than or
	 * equal to `this.expiredGen` is to be removed.
	 *
	 * @return number of events expunged from the shadow graph
	 */
	public synchronized int expire() {
		final HashMap<ShadowEvent, Boolean> tipRemove = new HashMap<>();
		final AtomicInteger count = new AtomicInteger();

		for (final ShadowEvent tip : tips) {
			count.addAndGet(shadowGraph.removeStrictSelfAncestry(tip, this::expired));
			tipRemove.put(tip, expired(tip));
		}

		tipRemove.forEach((ShadowEvent t, Boolean remove) -> {
			if (remove) {
				count.addAndGet(shadowGraph.removeSelfAncestry(t, this::expired));
				tips.remove(t);
			}
		});

		return count.get();
	}

	/**
	 * Get the shadow event that references a hashgraph event instance.
	 *
	 * @param e
	 * 		The event.
	 * @return the shadow event that references an event
	 */
	public synchronized ShadowEvent shadow(final Event e) {
		return shadowGraph.shadow(e);
	}

	/**
	 * Get the shadow event that references a hashgraph event instance
	 * with a given hash.
	 *
	 * @param h
	 * 		The event hash
	 * @return the shadow event that references an event with the given hash
	 */
	public synchronized ShadowEvent shadow(final Hash h) {
		return shadowGraph.shadow(h);
	}

	/**
	 * Get a hashgraph event from a hash
	 *
	 * @param h
	 * 		the hash
	 * @return the hashgraph event, if there is one in {@code this} shadow graph, else `null`
	 */
	public synchronized EventImpl hashgraphEvent(final Hash h) {
		final ShadowEvent shadow = shadow(h);
		if (shadow == null) {
			return null;
		} else {
			return (EventImpl) shadow.getEvent();
		}
	}

	/**
	 * If Event `e` is insertable, then insert it and update the tip Event set, else do nothing.
	 *
	 * @param e
	 * 		The event reference to insert.
	 * @return true iff e was inserted
	 */
	public synchronized boolean addEvent(final Event e) {
		final InsertableStatus status = insertable(e);

		if (status == InsertableStatus.INSERTABLE) {
			final ShadowEvent s = shadowGraph.insert(e);
			tips.add(s);
			tips.remove(s.getSelfParent());
			return true;
		} else {
			if (status == InsertableStatus.EXPIRED_EVENT) {
				LOG.debug(
						RECONNECT.getMarker(),
						"`addEvent`: did not insert, status is {} for event {}, expiredGen = {}",
						insertable(e),
						SyncLogging.getSyncLogString(e),
						expiredGen);
			} else {
				LOG.debug(
						RECONNECT.getMarker(),
						"`addEvent`: did not insert, status is {} for event {}",
						insertable(e),
						SyncLogging.getSyncLogString(e));
			}

			return false;
		}
	}

	/**
	 * Get the number of tips in this node's shadow graph at time of call.
	 *
	 * @return The number of tips
	 */
	public synchronized int getNumTips() {
		return tips.size();
	}

	/**
	 * Get the total number of shadow events in this node's shadow graph at time of call.
	 *
	 * @return The number of events
	 */
	public synchronized int getNumShadowEvents() {
		return shadowGraph.getNumShadowEvents();
	}

	/**
	 * Get the most recent expired generation used by this shadow graph at time of call.
	 *
	 * @return The most recent expired generation
	 */
	public synchronized long getExpiredGeneration() {
		return expiredGen;
	}

	/**
	 * Update the expired generation this SGM instance will use to remove expired shadow events.
	 *
	 * @param expiredGen
	 * 		The most recent expired generation
	 */
	public synchronized void setExpiredGeneration(final long expiredGen) {
		// 28 May 2021
		// Disabling this check to stabilize initial release of reconnect.
		// This is paired with disabled checks for decreased expired generation in functions
		// ConsensusImpl.updateMaxRoundGeneration and ConsensusImpl.updateMinRoundGeneration.
//		if (expiredGen < this.expiredGen) {
//			final String msg = String.format(
//					"Shadow graph expired generation can not be decreased. Change %s -> %s disallowed.",
//					this.expiredGen,
//					expiredGen);
//			throw new IllegalArgumentException(msg);
//		}

		this.expiredGen = expiredGen;
	}

	/**
	 * Get a reference to the hash set of tips for this shadow graph at time of call.
	 *
	 * @return The current tip hash set
	 */
	public synchronized Set<ShadowEvent> getTips() {
		return tips;
	}

	/**
	 * Identify the set of tip events in this node's shadow graph at time of call.
	 *
	 * Used only for testing.
	 */
	private void identifyTips() {
		tips.clear();
		for (final ShadowEvent shadowEvent : shadowGraph.getShadowEvents()) {
			if (shadowEvent.isTip()) {
				tips.add(shadowEvent);
			}
		}
	}


	/**
	 * Predicate to determine if an event referenced by a shadow event has expired.
	 * A shadow is expired iff the event it references is expired.
	 *
	 * @param s
	 * 		The shadow event.
	 * @return true iff the given shadow is expired
	 */
	private synchronized boolean expired(final ShadowEvent s) {
		return s.getEvent().getGeneration() <= expiredGen;
	}


	/**
	 * Apply a DFS dependency sort to {@code sendList}.
	 *
	 * @param sendList
	 * 		The list of events to sort.
	 */
	private static void sort(final List<EventImpl> sendList) {
		sendList.sort((EventImpl e1, EventImpl e2) -> (int) (e1.getGeneration() - e2.getGeneration()));
	}


	/**
	 * Add strict self-descendants of x to send list and remove from working tips
	 *
	 * @param sendList
	 * 		the list of events to be sent (possibly complete after this function executes).
	 * @param workingTips
	 * 		the set of working tips for this synchronization at time of this method call.
	 * @param sendingTips
	 * 		the full list of sending tips to determined at the beginning of this synchronization.
	 * @param maxTipGenerations
	 * 		the {@link List} of maximum tip generations per creator, computed from the {@code sendingTips} list.
	 * @param maxTipGeneration
	 * 		the maximum generation of all tips in the {@code sendingTips} list.
	 * @param visited
	 * 		the {@link Set} used to track visited state of each node.
	 * @param receivedTip
	 * 		the node from which to begin the search.
	 */
	private void processStrictSelfDescendants(
			final List<EventImpl> sendList,
			final Set<ShadowEvent> workingTips,
			final Set<Hash> sendingTips,
			final List<Long> maxTipGenerations,
			final long maxTipGeneration,
			final HashSet<ShadowEvent> visited,
			final ShadowEvent receivedTip) {
		for (final ShadowEvent event : receivedTip.getSelfChildren()) {
			processSelfDescendants(sendList, workingTips, sendingTips, maxTipGenerations, maxTipGeneration, visited,
					event);
		}
	}


	/**
	 * Add self-descendants of x to send list and remove from working tips
	 *
	 * @param sendList
	 * 		the list of events to be sent (possibly complete after this function executes).
	 * @param workingTips
	 * 		the set of working tips for this synchronization at time of this method call.
	 * @param sendingTips
	 * 		the full list of sending tips to determined at the beginning of this synchronization.
	 * @param maxTipGenerations
	 * 		the {@link List} of maximum tip generations per creator, computed from the {@code sendingTips} list.
	 * @param maxTipGeneration
	 * 		the maximum generation of all tips in the {@code sendingTips} list.
	 * @param visited
	 * 		the {@link Set} used to track visited state of each node.
	 * @param start
	 * 		the node from which to begin the search.
	 */
	private void processSelfDescendants(
			final List<EventImpl> sendList,
			final Set<ShadowEvent> workingTips,
			final Set<Hash> sendingTips,
			final List<Long> maxTipGenerations,
			final long maxTipGeneration,
			final HashSet<ShadowEvent> visited,
			final ShadowEvent start) {

		if (visited.contains(start)) {
			return;
		}

		final Deque<ShadowEvent> stack = new ArrayDeque<>();

		stack.push(start);
		while (!stack.isEmpty()) {
			final ShadowEvent cur = stack.pop();
			visited.add(cur);

			// push next
			if (!sendingTips.contains(cur.getEvent().getBaseHash())) {
				for (final ShadowEvent selfChild : cur.getSelfChildren()) {
					if (selfChild.getEvent().getGeneration() <= maxTipGeneration && !visited.contains(selfChild)) {
						stack.push(selfChild);
					}
				}
			}

			sendList.add((EventImpl) cur.getEvent());
			workingTips.remove(cur);
		}
	}


	/**
	 * Determine whether an event is insertable at time of call.
	 *
	 * @param e
	 * 		The event to evaluate
	 * @return An insertable status, indicating whether the event can be inserted, and if not, the reason it can not be
	 * 		inserted.
	 */
	private InsertableStatus insertable(final Event e) {
		if (e == null) {
			return InsertableStatus.NULL_EVENT;
		}

		// No multiple insertions
		if (shadow(e) != null) {
			return InsertableStatus.DUPLICATE_SHADOW_EVENT;
		}

		// An expired event will not be referenced in the graph.
		if (expired(e)) {
			return InsertableStatus.EXPIRED_EVENT;
		}

		final boolean hasOP = e.getOtherParent() != null;
		final boolean hasSP = e.getSelfParent() != null;

		// If e has an unexpired parent that is not already referenced
		// by the shadow graph, then do not insert e.
		if (hasOP) {
			final boolean knownOP = shadowGraph.shadow(e.getOtherParent()) != null;
			final boolean expiredOP = expired(e.getOtherParent());
			if (!knownOP && !expiredOP) {
				return InsertableStatus.UNKNOWN_CURRENT_OTHER_PARENT;
			}
		}

		if (hasSP) {
			final boolean knownSP = shadowGraph.shadow(e.getSelfParent()) != null;
			final boolean expiredSP = expired(e.getSelfParent());
			if (!knownSP && !expiredSP) {
				return InsertableStatus.UNKNOWN_CURRENT_SELF_PARENT;
			}
		}

		// If both parents are null, then insertion is allowed. This will create
		// a new tree in the forest view of the graph.
		return InsertableStatus.INSERTABLE;
	}

	private enum InsertableStatus {
		INSERTABLE,
		NULL_EVENT,
		DUPLICATE_SHADOW_EVENT,
		EXPIRED_EVENT,
		UNKNOWN_CURRENT_SELF_PARENT,
		UNKNOWN_CURRENT_OTHER_PARENT
	}
}



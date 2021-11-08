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
import com.swirlds.platform.EventImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data used during gossiping. This type is instantiated by {@code NodeSynchronizerImpl}.
 * These fields are assigned by a {@link ShadowGraphManager}.
 */
public final class SyncData {
	/**
	 * The list of hashgraph events to send.
	 * NodeSynchronizerBase uses the ShadowGraphManager to
	 * assembled this list.
	 */
	private final List<EventImpl> sendList = new LinkedList<>();

	/**
	 * The tip hashes received from the remote node. NodeSynchronizer updates
	 * the shadow graph with this list.
	 */
	private final List<Hash> receivedTipHashes = new LinkedList<>();

	/**
	 * The set of tips used to determine what we need to send to our peer.
	 * At beginning of gossip session, this is set equal to the sendingTips.
	 * These events are eliminated during the course of the gossip session.
	 * Most of the time, all of these should be eliminated by the time we reach finishSendEventList.
	 */
	private final Set<ShadowEvent> workingTips = new HashSet<>();

	/**
	 * The set of tip hashes used during phase 1 of the gossip protocol. Due to the non-deterministic iteration of the
	 * most {@link Set} implementations these must be properly ordered prior to transmission.
	 */
	private final Set<Hash> sendingTips = new HashSet<>();

	/**
	 * The list of tips used during phase 1 and phase 3 of the gossip protocol. This list is initialized and the
	 * beginning of the sync and is stored in proper order. This list must never be modified after initialization.
	 */
	private final List<ShadowEvent> sendingTipList = new ArrayList<>();

	/**
	 * The maximum generation of all tips in the {@code sendingTipList}. This value is initialized once at the beginning
	 * of the sync.
	 */
	private long maxTipGeneration = EventImpl.NO_EVENT_GEN;

	/**
	 * The list of the maximum tip generation per creator as derived from the {@code sendingTipList}. The list index
	 * corresponds to the node/creator index and the value is the maximum generation for all tips from that creator.
	 */
	private final List<Long> maxTipGenerations = new ArrayList<>();

	/**
	 * Indicates whether the phase 3 fork absorption loops were engaged at least once during this synchronization.
	 */
	private boolean tipAbsorptionActive = false;

	/**
	 * Get the sending tips as a list
	 *
	 * @return the list of sending tips
	 */
	public List<ShadowEvent> getSendingTipList() {
		return sendingTipList;
	}

	/**
	 * Set the max generation of all of the tips sent to the peer
	 *
	 * @param sendingTipList
	 * 		the list of tip events
	 */
	public void setMaxTipGenerations(final List<ShadowEvent> sendingTipList) {
		long maxCreatorId = 0;
		for (final ShadowEvent tip : sendingTipList) {
			maxCreatorId = Math.max(maxCreatorId, tip.getEvent().getCreatorId());
		}

		for (int i = 0; i <= maxCreatorId; ++i) {
			maxTipGenerations.add(EventImpl.NO_EVENT_GEN);
		}

		for (final ShadowEvent tip : sendingTipList) {
			maxTipGeneration = Math.max(maxTipGeneration, tip.getEvent().getGeneration());

			final int i = (int) tip.getEvent().getCreatorId();
			final long max = Math.max(maxTipGenerations.get(i), tip.getEvent().getGeneration());
			maxTipGenerations.set(i, max);
		}
	}

	/**
	 * Gets the maximum generation across all the sending tips.
	 *
	 * @return the generation number of the most recent sending tip
	 */
	public long getMaxTipGeneration() {
		return maxTipGeneration;
	}

	/**
	 * Gets a list that contains the maximum generation for each creator. The list index is the creator index and the
	 * value is the maximum generation for that creator.
	 *
	 * @return a list containing the maximum generation per creator.
	 */
	public List<Long> getMaxTipGenerations() {
		return maxTipGenerations;
	}

	/**
	 * Provides direct access to the unsorted set of tip hashes. This must not be used for transmission of hashes during
	 * synchronization. Instead the {@link #getSendingTipHashes()} method should be used.
	 *
	 * @return the tips sent to the peer
	 */
	public Set<Hash> getSendingTips() {
		return sendingTips;
	}

	/**
	 * Sorts the hashes contained in the {@code sendingTips} set into proper transmission order.
	 *
	 * @return the hashes of the tips sent to the peer
	 */
	public List<Hash> getSendingTipHashes() {
		final List<Hash> tipHashes = new ArrayList<>(getSendingTipList().size());
		for (int i = 0; i < getSendingTipList().size(); ++i) {
			tipHashes.add(i, getSendingTipList().get(i).getEventBaseHash());
		}

		return tipHashes;
	}

	/**
	 * The set of hashes which are marked "true", for sync.
	 * Used for sync calculations.
	 */
	private final Set<Hash> sync = new HashSet<>();

	/**
	 * The set of hashes which are marked "true", for search.
	 * Used for sync calculations.
	 */
	private final Set<Hash> search = new HashSet<>();

	/**
	 * Get the list of {@code EventImpl}s to send
	 *
	 * @return the list of {@code EventImpl}s to send
	 */
	public List<EventImpl> getSendList() {
		return sendList;
	}

	/**
	 * Get the list of tip hashes this node hsa received during a given gossip session
	 *
	 * @return the list of tip hashes this node hsa received during a given gossip session
	 */
	public List<Hash> getReceivedTipHashes() {
		return receivedTipHashes;
	}

	/**
	 * Get the working tip set
	 *
	 * @return the working tip set
	 */
	public Set<ShadowEvent> getWorkingTips() {
		return workingTips;
	}

	/**
	 * Mark a shadow event for sync. Used in Phase 1 of the shadow graph gossip implementation.
	 *
	 * @param s
	 * 		the shadow event to mark
	 */
	public void markForSync(final ShadowEvent s) {
		sync.add(s.getEventBaseHash());
	}

	/**
	 * Mark a shadow event for sync, given its hash. Used in Phase 1 of the shadow graph gossip implementation.
	 *
	 * @param h
	 * 		the hash which identifies the shadow event to mark
	 */
	public void markForSync(final Hash h) {
		sync.add(h);
	}

	/**
	 * Mark a shadow event for search, used only Phase 3 of the shadow graph gossip implementation.
	 *
	 * @param s
	 * 		the shadow event to mark
	 */
	public void markForSearch(final ShadowEvent s) {
		search.add(s.getEventBaseHash());
	}

	/**
	 * Mark a shadow event for search, given its hash.
	 *
	 * @param h
	 * 		the hash which identifies the shadow event to mark
	 */
	public void markForSearch(final Hash h) {
		search.add(h);
	}

	/**
	 * Is a given shadow event marked for sync by the gossip protocol? Evaluated in shadow graph gossip Phase 3.
	 *
	 * @param s
	 * 		the shadow event
	 * @return whether the given shadow event is marked for sync
	 */
	public boolean markedForSync(final ShadowEvent s) {
		return sync.contains(s.getEventBaseHash());
	}

	/**
	 * Is a given shadow event marked for search by the gossip protocol? Evaluated in shadow graph gossip Phase 3.
	 * This value is the final arbiter of whether en event found by a graph descendant search is selected for
	 * transmission during gossip.
	 *
	 * @param s
	 * 		the shadow event
	 * @return whether the given shadow event is marked for sync
	 */
	public boolean markedForSearch(final ShadowEvent s) {
		return search.contains(s.getEventBaseHash());
	}

	/**
	 * Get the number of shadow events marked for sync
	 *
	 * @return the number of shadow events marked for sync
	 */
	public int getNumMarkedForSync() {
		return sync.size();
	}

	/**
	 * Get the number of shadow events marked for search
	 *
	 * @return the number of shadow events marked for search
	 */
	public int getNumMarkedForSearch() {
		return search.size();
	}

	/**
	 * Sets the {@link #isTipAbsorptionActive()} to {@code true} after entering the outermost loop of phase 3 gossip.
	 * This method should only be called from the {@link ShadowGraphManager#finishSendEventList(SyncData)} method.
	 */
	protected void notifyTipAbsorptionActivated() {
		this.tipAbsorptionActive = true;
	}

	/**
	 * Tracks whether the phase 3 loops have been engaged at any time during this synchronization. This method is
	 * used by {@code NodeSynchronizerImpl} to update the platform statistics.
	 *
	 * @return {@code true} if the phase 3 loops where engaged at any time during this synchronization.
	 */
	public boolean isTipAbsorptionActive() {
		return tipAbsorptionActive;
	}

	/**
	 * Computes the number of creators that have more than one tip. If a single creator has more than two tips, this
	 * method will only report once for each such creator. The execution time cost for this method is O(T + N) where
	 * T is the number of tips including all forks and N is the number of network nodes. There is some memory overhead,
	 * but it is fairly nominal in favor of the time complexity savings.
	 *
	 * @return the number of event creators that have more than one tip.
	 */
	public int computeMultiTipCount() {
		// The number of tips per creator encountered when iterating over the sending tips
		final Map<Long, Integer> tipCountByCreator = new HashMap<>();

		// Make a single O(N) where N is the number of tips including all forks. Typically, N will be equal to the
		// number of network nodes.
		for (final ShadowEvent tip : sendingTipList) {
			tipCountByCreator.compute(tip.getEvent().getCreatorId(), (k, v) -> (v != null) ? v + 1 : 1);
		}

		// Walk the entrySet() which is O(N) where N is the number network nodes. This is still more efficient than a
		// O(N^2) loop.
		int creatorsWithForks = 0;
		for (final Map.Entry<Long, Integer> entry : tipCountByCreator.entrySet()) {
			// If the number of tips for a given creator is greater than 1 then we have a fork.
			// This map is broken down by creator ID already as the key so this is guaranteed to be a single increment
			// for each creator with a fork. Therefore, this holds to the method contract.
			if (entry.getValue() > 1) {
				creatorsWithForks++;
			}
		}

		return creatorsWithForks; // total number of unique creators with more than one tip
	}
}




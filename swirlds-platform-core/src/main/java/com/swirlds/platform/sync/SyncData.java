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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Data used during gossiping. This type is instantiated by {@code NodeSynchronizerImpl}.
 * These fields are assigned by a {@link SyncShadowGraphManager}.
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
	private final Set<SyncShadowEvent> workingTips = new HashSet<>();

	private final Set<Hash> sendingTips = new HashSet<>();

	private final List<SyncShadowEvent> sendingTipList = new ArrayList<>();

	private long maxTipGeneration = EventImpl.NO_EVENT_GEN;

	private final List<Long> maxTipGenerations = new ArrayList<>();

	private boolean tipAbsorptionActive = false;

	/**
	 * Get the sending tips as a list
	 *
	 * @return the list of sending tips
	 */
	public List<SyncShadowEvent> getSendingTipList() {
		return sendingTipList;
	}

	/**
	 * Set the max generation of all of the tips sent to the peer
	 *
	 * @param sendingTipList
	 * 		the list of tip events
	 */
	public void setMaxTipGenerations(List<SyncShadowEvent> sendingTipList) {
		long maxCreatorId = 0;
		for (SyncShadowEvent tip : sendingTipList) {
			maxCreatorId = Math.max(maxCreatorId, tip.getEvent().getCreatorId());
		}

		for (int i = 0; i <= maxCreatorId; ++i) {
			maxTipGenerations.add(EventImpl.NO_EVENT_GEN);
		}

		for (SyncShadowEvent tip : sendingTipList) {
			maxTipGeneration = Math.max(maxTipGeneration, tip.getEvent().getGeneration());

			final int i = (int) tip.getEvent().getCreatorId();
			final long max = Math.max(maxTipGenerations.get(i), tip.getEvent().getGeneration());
			maxTipGenerations.set(i, max);
		}
	}

	public long getMaxTipGeneration() {
		return maxTipGeneration;
	}

	/**
	 * Get the maximum generation of all of the sending tips
	 *
	 * @return the max generation number
	 */
	public List<Long> getMaxTipGenerations() {
		return maxTipGenerations;
	}

	/**
	 * @return the tips sent to the peer
	 */
	public Set<Hash> getSendingTips() {
		return sendingTips;
	}

	/**
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
	public Set<SyncShadowEvent> getWorkingTips() {
		return workingTips;
	}

	/**
	 * Mark a shadow event for sync. Used in Phase 1 of the shadow graph gossip implementation.
	 *
	 * @param s
	 * 		the shadow event to mark
	 */
	public void markForSync(final SyncShadowEvent s) {
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
	public void markForSearch(final SyncShadowEvent s) {
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
	public boolean markedForSync(final SyncShadowEvent s) {
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
	public boolean markedForSearch(final SyncShadowEvent s) {
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

	protected void notifyTipAbsorptionActivated() {
		this.tipAbsorptionActive = true;
	}

	public boolean isTipAbsorptionActive() {
		return tipAbsorptionActive;
	}

	/**
	 * Computes the number of creators that have more than one tip. If a single creator has more than two tips, this
	 * method will only report once for each such creator.
	 *
	 * @return the number of event creators that have more than one tip.
	 */
	public int computeMultiTipCount() {
		final Set<Long> multiTipCreators = new HashSet<>();

		final Iterator<SyncShadowEvent> iter = sendingTipList.iterator();
		while (iter.hasNext()) {
			final SyncShadowEvent left = iter.next();
			final SyncShadowEvent right = (iter.hasNext()) ? iter.next() : null;

			// If right is null then we are at the end of the list so we can break out of the loop
			if (right == null) {
				break;
			}

			if (left.getEvent().getCreatorId() == right.getEvent().getCreatorId()) {
				multiTipCreators.add(left.getEvent().getCreatorId()); // hash set to deduplicate the values
			}
		}

		return multiTipCreators.size(); // total number of unique creators with more than one tip
	}
}




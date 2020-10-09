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
import com.swirlds.common.events.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.swirlds.common.crypto.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.SYNC_SGM;

class SyncShadowGraphManager {
	SyncShadowGraph shadowGraph;
	HashSet<SyncShadowEvent> tips;
	long expiredGen;
	long currSyncMark = 0, currSearchMark = 0;

	HashSet<SyncShadowEvent> workingTips;
	List<Hash> receivedTipHashes;
	LinkedHashSet<EventImpl> sendList;
	List<Boolean> sendFlags, receivedTipFlags;

	SyncShadowGraphManager(AbstractHashgraph hashgraph) {
		this(new SyncShadowGraph(hashgraph), hashgraph.getMinGenerationNonAncient() - 1);
	}

	SyncShadowGraphManager(SyncShadowGraph shadowGraph) {
		this(shadowGraph, -1);
	}

	SyncShadowGraphManager(SyncShadowGraph shadowGraph, long expiredGen) {
		this.shadowGraph = shadowGraph;
		this.expiredGen = expiredGen;

		this.tips = new HashSet<>();
		getTips();

		this.workingTips = new HashSet<>();
		this.receivedTipHashes = new ArrayList<>();
		this.sendList = new LinkedHashSet<>();
		this.sendFlags = new ArrayList<>();
		this.receivedTipFlags = new ArrayList<>();
	}

	private List<Event> getUpdateEvents(AbstractHashgraph hashgraph) {

		Event[] events;
		if(tips.isEmpty())
			events = hashgraph.getRecentEvents(expiredGen + 1);
		else {
			AtomicLong minTipGeneration = new AtomicLong(Long.MAX_VALUE);
			tips.forEach((SyncShadowEvent s)
					-> minTipGeneration.set(Math.min(minTipGeneration.get(), s.event.getGeneration())));
			events = hashgraph.getRecentEvents(minTipGeneration.get());
		}

		List<Event> updates = new ArrayList<>();
		Arrays.stream(events).forEach((Event e) ->
		{
			if (!expired(e))
				if (shadowGraph.shadow(e) == null)
					updates.add(e);
		});

		updates.sort((Event a, Event b) -> (int)(a.getGeneration() - b.getGeneration()));

		return updates;
	}

	synchronized void updateByGeneration(AbstractHashgraph hashgraph, NodeId selfId, NodeId otherId, boolean caller) {
		String connectionLogString = "";
		if(caller)
			connectionLogString = String.format("%s -> %s", selfId, otherId);
		else
			connectionLogString = String.format("%s <- %s", selfId, otherId);

		log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Hashgraph has {} Events", hashgraph.getNumEvents());
		log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Shadow graph@entry has {} events and {} tips", shadowGraph.shadowEvents.size(), tips.size());
		for(SyncShadowEvent tip : tips)
			log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`:    gen = {}, tip = {}", tip.event.getGeneration(), tip.getBaseEventHash().toString().substring(0, 4));

		int nremoved = expire(hashgraph.getMinGenerationNonAncient() - 1);
		log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Removed {} expired shadow events, expiredGen = {}", nremoved, expiredGen);

		List<Event> updates = getUpdateEvents(hashgraph);

		log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Got {} update events from hashgraph" + (updates.size() > 0 ? " (>0)" : ""), updates.size());
		for(Event update : updates) {
			String h = update.getBaseHash().toString().substring(0, 4);
			String sph = update.getSelfParent()  != null ? update.getSelfParent().getBaseHash().toString().substring(0, 4) : "null";
			String oph = update.getOtherParent() != null ? update.getOtherParent().getBaseHash().toString().substring(0, 4) : "null";
			log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`:   gen = {}, update = {}, sp = {}, op = {}", update.getGeneration(), h, sph, oph);
		}

		log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Updating from {} recent events", updates.size());
		HashSet<Event> inserted = new HashSet<>();
		while (inserted.size() != updates.size()) {
			for (Event event : updates) {
				Event e = event;
				if (inserted.contains(e))
					continue;

				if(shadowGraph.insert(e)) {
					SyncShadowEvent s = shadowGraph.shadow(e);
					if(tips.contains(s.selfParent)) {
						tips.remove(s.selfParent);
						tips.add(s);
					}

					if(s.selfChildren.size() == 0)
						tips.add(s);

					inserted.add(e);
				}

//				if (insert(e))
//					inserted.add(e);
			}
			log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`: Inserted {} so far", inserted.size());
		}

		getTips();

		log.debug(SYNC_SGM.getMarker(),
				connectionLogString + " `updateByGeneration`: Done updating from hashgraph. Shadow graph@exit has {} events and {} tips", shadowGraph.shadowEvents.size(), tips.size());
		for(SyncShadowEvent tip : tips)
			log.debug(SYNC_SGM.getMarker(), connectionLogString + " `updateByGeneration`:    gen = {}, tip = {}", tip.event.getGeneration(), tip.getBaseEventHash().toString().substring(0, 4));

	}

	// Sort by parent-dependency, from current tip set.
	void descendantDFSSort(List<EventImpl> sorted, List<EventImpl> updates) {
		
	}

	int verify(Hashgraph hashgraph) {
		EventImpl[] events = hashgraph.getAllEvents();

		if(events.length == 0)
			return 0;

		for (EventImpl e : events) {
			if(expired(e))
				continue;

			SyncShadowEvent s = shadow(e);
			if (s == null)
				// This should be a transitory result, when the hashgraph has created a new event
				// (on intake queue thread) since the creation of this shadow graph.
				return 1;
			if (!s.event.equals(e))
				// Internal structural failure, should never happen. This is a sanity check, only for
				// thoroughness.
				return 2;
		}

		for(EventImpl e : events) {
			if(expired(e))
				continue;

			SyncShadowEvent s = shadow(e);
			if(e.getSelfParent() != null)
				if (s.selfParent == null)
					// In general, this is not a failure. The hashgraph from which this shadow graph
					// was constructed may contain expired events (with expiration as defined by the
					// hashgraph object), but this return value should be transitory in the call sequence.
					return 3;

			if(e.getOtherParent() != null)
				if (s.otherParent == null)
					// In general, this is not a failure. The hashgraph from which this shadow graph
					// was constructed may contain expired events (with expiration as defined by the
					// hashgraph object), but this return value should be transitory in the call sequence.
					return 4;

			if(e.getSelfParent() == null)
				if(s.selfParent != null)
					// The shadow graph should not contain events that the hashgraph no longer contains.
					return 5;

			if(e.getOtherParent() == null)
				if(s.otherParent != null)
					// The shadow graph should not contain events that the hashgraph no longer contains.
					return 6;

			// Internal structural failures: these should never happen.
			if(s.selfParent != null && !s.selfParent.event.equals(e.getSelfParent()))
				return 7;
			if(s.otherParent != null && !s.otherParent.event.equals(e.getOtherParent()))
				return 8;
			if(s.selfParent != null && !s.selfParent.selfChildren.contains(s))
				return 9;
			if(s.otherParent != null && !s.otherParent.otherChildren.contains(s))
				return 10;
			for(SyncShadowEvent sc : s.selfChildren)
				if(sc.selfParent != null && !sc.selfParent.event.equals(e))
					return 11;
			for(SyncShadowEvent oc : s.otherChildren)
				if(oc.otherParent != null && !oc.otherParent.event.equals(e))
					return 12;
		}

		// Internal failure: tip identification. Should never happen.
		for(EventImpl e : events) {
			if(expired(e))
				continue;

			boolean isSelfParent = Arrays.stream(events).anyMatch(
				(EventImpl e0) -> e0.getSelfParent() != null && e0.getSelfParent().equals(e));

			if(isSelfParent && tips.contains(shadow(e)))
				return 13;

			if(!isSelfParent && !tips.contains(shadow(e)))
				return 14;
		}

		return 0;
	}

	void resetForSync() {
		workingTips.clear();
		receivedTipHashes.clear();
		sendList.clear();
		sendFlags.clear();
		receivedTipFlags.clear();
		currSearchMark++;
		currSyncMark++;
	}

	List<Hash> getSendTipHashes() {
		List<Hash> tipHashes = new ArrayList<>();
		tips.forEach((SyncShadowEvent s) -> tipHashes.add(s.getBaseEventHash()));
		return tipHashes;
	}

	void setReceivedTipHashes(List<Hash> receivedTipHashes) {
		this.sendList.clear();
		this.workingTips = new HashSet<>(tips);
		this.receivedTipHashes = new ArrayList<>(receivedTipHashes);
		this.receivedTipHashes.forEach((Hash h) -> {
			SyncShadowEvent receivedTip = shadowGraph.shadow(h);
			if (receivedTip != null) {
				receivedTip.markForSync();
				workingTips.remove(receivedTip);
				this.addSSDsToSendListAndRemoveFromWorkingTips(receivedTip);
			}});
	}

	List<Boolean> getSendTipFlags() {
		this.sendFlags.clear();
		for(int i = 0; i < this.receivedTipHashes.size(); ++i) {
			SyncShadowEvent receivedTip = shadowGraph.shadow(receivedTipHashes.get(i));
			if(receivedTip != null && receivedTip.selfChildren.size() > 0)
				sendFlags.add(true);
			else
				sendFlags.add(false);
		}

		return sendFlags;
	}

	void setReceivedTipFlags(List<Boolean> receivedTipFlags) {
		this.receivedTipFlags = new ArrayList<>(receivedTipFlags.size());
		this.receivedTipFlags.addAll(receivedTipFlags);

		List<Hash> tipHashes = getSendTipHashes();
		for(int i = 0; i < receivedTipFlags.size(); ++i) {
			Boolean b = receivedTipFlags.get(i);
			if(b) {
				shadowGraph.shadow(tipHashes.get(i)).markForSync();
				workingTips.remove(shadowGraph.shadow(tipHashes.get(i)));
			}
		}
	}

	private static final Logger log = LogManager.getLogger();

	List<EventImpl> getSendEventList(NodeId selfId, NodeId otherId) {
		log.debug(SYNC_SGM.getMarker(), "{} -> {} `getSendEventList`: finishing sendList, starting with {} events...", selfId, otherId, sendList.size());
		List<EventImpl> sendList = getSendEventList();
		log.debug(SYNC_SGM.getMarker(), "{} -> {} `getSendEventList`: ...done. sendList has {} events", selfId, otherId, sendList.size());
		return sendList;
	}

	List<EventImpl> getSendEventList() {
		Queue<EventImpl> sendQueue = new PriorityQueue<>();
		for(SyncShadowEvent workingTip : this.workingTips) {
			SyncShadowEvent y = workingTip;

			while(y != null) {

				for(SyncShadowEvent z : shadowGraph.graphDescendants(y)) {
					boolean zMarkedForSync = z.syncMark == currSyncMark;
					if (zMarkedForSync) {
						y.markForSearch();
						break;
					}
				}

				boolean yMarkedForSearch = y.searchMark == currSearchMark;
				if(!yMarkedForSearch)
					sendQueue.add((EventImpl)y.event);
				else
					break;

				y = y.selfParent;
			}
		}

		sendList.addAll(sendQueue);
		return new ArrayList<>(sendList);
	}

	void setReceivedEventList(List<EventImpl> receivedSendList) {
		for(EventImpl receivedEvent : receivedSendList) {
//			System.out.println(" receivedEvent = " + receivedEvent.getBaseHash().toString().substring(0, 4) + " seq. number = " + receivedEvent.getSeq());
			insert(receivedEvent);
		}
	}


	// Add strict self-descendants of x to send list
	private void addSSDsToSendListAndRemoveFromWorkingTips(SyncShadowEvent x) {
		for(SyncShadowEvent y : x.selfChildren)
			addSDsToSendListAndRemoveFromWorkingTips(y);
	}

	// Add self-descendants of x to send list
	private void addSDsToSendListAndRemoveFromWorkingTips(SyncShadowEvent y) {
		if(y == null)
			return;

		sendList.add((EventImpl)y.event);
		workingTips.remove(y);

		for(SyncShadowEvent y0 : y.selfChildren)
			addSDsToSendListAndRemoveFromWorkingTips(y0);
	}

	long getExpiredGeneration() {
		return expiredGen;
	}

	void setExpiredGeneration(long expiredGen) {
		if(expiredGen < this.expiredGen)
			throw new IllegalArgumentException("Expired generation can not be decreased.");
		else
			this.expiredGen = expiredGen;
	}

	private boolean expired(SyncShadowEvent s) {
		return s.event.getGeneration() <= expiredGen;
	}

	boolean expired(Event event) {
		return event.getGeneration() <= expiredGen;
	}


	/**
	 *
	 * @param newExpiredGeneration
	 * @return number of event expunged from the shadow graph
	 */
	int expire(long newExpiredGeneration) {
		if(newExpiredGeneration == expiredGen)
			return 0;

		setExpiredGeneration(newExpiredGeneration);
		return expire();
	}

	int expire() {
		HashMap<SyncShadowEvent, Boolean> tipRemove = new HashMap<>();
		AtomicInteger count = new AtomicInteger();
		for (SyncShadowEvent tip : tips) {
			count.addAndGet(shadowGraph.removeStrictAncestry(tip, this::expired));
			tipRemove.put(tip, expired(tip));
		}

		tipRemove.forEach((SyncShadowEvent t, Boolean remove) -> {
			if (remove)
				count.addAndGet(shadowGraph.removeAncestry(t));
		});

		getTips();
		return count.get();
	}

	SyncShadowEvent shadow(Event e) {
		return shadowGraph.shadow(e);
	}


	private int insertable(Event e) {
		if(e == null)
			return 1;

		// No multiple insertions
		if(shadow(e) != null)
			return 5;

		// An expired event will not be referenced in the graph.
		if (expired(e)){
//			System.out.println(((EventImpl) e).getBaseEventHashedData().getHash().toString().substring(0, 4) + ": not insertable (expired)");
			return 2;
		}

		boolean hasOP = e.getOtherParent() != null;
		boolean hasSP = e.getSelfParent() != null;

		boolean knownOP = hasOP && shadowGraph.shadow(e.getOtherParent()) != null;
		boolean knownSP = hasSP && shadowGraph.shadow(e.getSelfParent()) != null;

		boolean expiredOP = hasOP && expired(e.getOtherParent());
		boolean expiredSP = hasSP && expired(e.getSelfParent());

		boolean currentSP = !expiredSP;
		boolean currentOP = !expiredOP;

		// If e has an unexpired parent that is not already referenced
		// by the shadow graph, then do not insert e.
		if(hasOP)
			if (!knownOP && !expiredOP) {
//				System.out.println(((EventImpl) e).getBaseEventHashedData().getHash().toString().substring(0, 4) + ": not insertable (other parent unexpired and unknown)");
				return 3;
			}

		if(hasSP)
			if(!knownSP && !expiredSP) {
//				System.out.println(((EventImpl) e).getBaseEventHashedData().getHash().toString().substring(0, 4) + ": not insertable (self parent unexpired and unknown)");
				return 4;
			}

		// If both parents are null, then insertion is allowed. This will create
		// a new tree in the forest view of the graph.
		return 0;
	}

	boolean insert(Event e) {
		if(0 == insertable(e))
			if(shadowGraph.insert(e)) {
				SyncShadowEvent s = shadowGraph.shadow(e);
				if(tips.contains(s.selfParent)) {
					tips.remove(s.selfParent);
					tips.add(s);
				}

				if(s.selfChildren.size() == 0)
					tips.add(s);

				return true;
			}

		return false;
	}

	private void getTips() {
		tips.clear();
		for (SyncShadowEvent shadowEvent : shadowGraph.shadowEvents)
			if (shadowEvent.isTip())
				tips.add(shadowEvent);
	}

}



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
package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.Event;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.stats.SyncStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.SYNC_INFO;

/**
 * <p>A shadow graph is a lightweight replication of the hashgraph structure. It supports:</p>
 *
 * <ul>
 * <li>insertion of a shadow event</li>
 * <li>querying for a shadow event by hashgraph event base hash</li>
 * <li>querying for ancestors by shadow event</li>
 * <li>providing the current tips (shadow events with no self-child)</li>
 * <li>expiration of shadow events by generation</li>
 * <li>reservation of a generation of events to prevent event expiration</li>
 * </ul>
 *
 * <p>The shadow graph is thread safe.</p>
 */
public class ShadowGraph {

	private static final Logger LOG = LogManager.getLogger();

	/**
	 * The generation value for the first event created by a node.
	 */
	private static final long FIRST_GENERATION = 0;

	/** The generation value indicating that no generation is currently reserved. */
	public static final int NO_GENERATION_RESERVED = -1;

	/**
	 * The shadow graph represented in a map from has to shadow event.
	 */
	private final Map<Hash, ShadowEvent> hashToShadowEvent;

	/**
	 * Map from generation to all shadow events in that generation.
	 */
	private final Map<Long, Set<ShadowEvent>> generationToShadowEvent;

	/**
	 * The set of all tips for the shadow graph. A tip is an event with no self child (could have other children)
	 */
	private final HashSet<ShadowEvent> tips;

	/**
	 * The generation for which all older generations should be expired, when possible
	 */
	private long expireBelow;

	/**
	 * The oldest generation that has not yet been expired
	 */
	private long oldestGeneration;

	/**
	 * The list of all currently reserved generations and their number of reservations
	 */
	private final LinkedList<GenerationReservationImpl> reservationList;

	/**
	 * The stats instance to update
	 */
	private final SyncStats stats;

	/** the number of nodes in the network, used for debugging */
	private final int numberOfNodes;

	/**
	 * Constructs a new instance.
	 */
	public ShadowGraph(final SyncStats stats) {
		this(stats, -1);
	}

	public ShadowGraph(final SyncStats stats, final int numberOfNodes) {
		this.stats = stats;
		this.numberOfNodes = numberOfNodes;
		expireBelow = FIRST_GENERATION;
		oldestGeneration = FIRST_GENERATION;
		tips = new HashSet<>();
		hashToShadowEvent = new HashMap<>();
		generationToShadowEvent = new HashMap<>();
		reservationList = new LinkedList<>();
	}

	/**
	 * <p>Initializes the {@link ShadowGraph} with the given {@code events}. This method should be used after
	 * reconnect or restart. {@code events} must be ordered by generation, smallest to largest.</p>
	 *
	 * <p>A minimum generation is necessary because events loaded from signed state a could have generation gaps and are
	 * used in {@link com.swirlds.platform.Consensus}. {@link com.swirlds.platform.Consensus} will eventually expire its
	 * smallest generation and that generation must be present in the {@link ShadowGraph} or an exception is thrown, so
	 * we create empty generations to match {@link com.swirlds.platform.Consensus}.</p>
	 *
	 * @param events
	 * 		the events to add to the shadow graph
	 * @param minGeneration
	 * 		the generation to use as a minimum generation
	 * @throws IllegalArgumentException
	 * 		if argument is null or empty
	 */
	public synchronized void initFromEvents(final List<EventImpl> events, final long minGeneration) {
		if (events == null || events.isEmpty()) {
			throw new IllegalArgumentException("events must not be null or empty");
		}

		// Set this to the oldest generation in the event list, so we can determine if parent events are expired,
		// therefore allowing the event to be inserted.
		oldestGeneration = events.get(0).getGeneration();
		expireBelow = events.get(0).getGeneration();

		for (EventImpl event : events) {
			// if an issue like this occurs, we still might be in a situation where we could continue running, that's
			// why we catch and log these exceptions
			try {
				addEvent(event);
			} catch (ShadowGraphInsertionException e) {
				LOG.error(EXCEPTION.getMarker(), "unable to insert event {}", event.toShortString(), e);
			}
		}

		// if we are missing some generation, we will create empty ones to match Consensus
		while (expireBelow > minGeneration) {
			expireBelow--;
			generationToShadowEvent.put(expireBelow, new HashSet<>());
		}

		// Now that events are added, update (decrease) the oldest generation to match the expireBelow value in case it
		// was decreased to match the minGeneration.
		oldestGeneration = expireBelow;

		LOG.info(STARTUP.getMarker(),
				"Shadow graph initialized from events. Provided minGeneration = {}. Calculated oldestGeneration = {}",
				minGeneration, oldestGeneration);
	}

	/**
	 * Reset the shadow graph manager to its constructed state.
	 */
	public synchronized void clear() {
		expireBelow = FIRST_GENERATION;
		oldestGeneration = FIRST_GENERATION;
		disconnectShadowEvents();
		tips.clear();
		hashToShadowEvent.clear();
		generationToShadowEvent.clear();
		reservationList.clear();
	}

	/**
	 * Disconnect all shadow events to help the garbage collector.
	 */
	private void disconnectShadowEvents() {
		for (ShadowEvent shadow : hashToShadowEvent.values()) {
			shadow.disconnect();
		}
	}

	/**
	 * Reserves the events in generation {@code expireBelow}. A reservation prevents events in that generation and later
	 * (higher) generations from being expired from the shadow graph.
	 *
	 * @return the reservation instance with the reserved generation
	 */
	public synchronized GenerationReservation reserve() {
		if (reservationList.isEmpty()) {
			return newReservation();
		}
		GenerationReservationImpl lastReservation = reservationList.getLast();
		if (lastReservation.getGeneration() == expireBelow) {
			lastReservation.incrementReservations();
			return lastReservation;
		} else {
			return newReservation();
		}
	}

	/**
	 * Determines if the provided {@code hash} is in the shadow graph.
	 *
	 * @param hash
	 * 		the hash to look for
	 * @return true if the hash matches the hash of a shadow event in the shadow graph, false otherwise
	 */
	public synchronized boolean isHashInGraph(final Hash hash) {
		return hashToShadowEvent.containsKey(hash);
	}

	/**
	 * <p>Returns the ancestors of the provided {@code events} that pass the provided {@code predicate} using a
	 * depth-first search. The provided {@code events} are not included in the return set. Searching stops at nodes
	 * that have no parents, or nodes that do not pass the {@code predicate}.</p>
	 *
	 * <p>It is safe for this method not to be synchronized because:</p>
	 * <ol>
	 *     <li>this method does not modify any data</li>
	 *     <li>adding events to the the graph does not affect ancestors</li>
	 *     <li>checks for expired parent events are atomic</li>
	 * </ol>
	 * <p>Note: This method is always accessed after a call to a synchronized {@link ShadowGraph} method, like
	 * {@link #getTips()}, which acts as a memory gate and causes the calling thread to read the latest values for all
	 * variables from memory, including {@link ShadowEvent} links.</p>
	 *
	 * @param events
	 * 		the event to find ancestors of
	 * @param predicate
	 * 		determines whether or not to add the ancestor to the return list
	 * @return the set of matching ancestors
	 */
	public Set<ShadowEvent> findAncestors(final Iterable<ShadowEvent> events,
			final Predicate<ShadowEvent> predicate) {
		final HashSet<ShadowEvent> ancestors = new HashSet<>();
		for (ShadowEvent event : events) {
			// add ancestors that have not already been found and that pass the predicate
			findAncestors(
					ancestors,
					event,
					e -> !ancestors.contains(e) && !expired(e.getEvent()) && predicate.test(e));
		}
		return ancestors;
	}

	/**
	 * Private method that searches for ancestors and takes a HashSet as input. This method exists for efficiency, when
	 * looking for ancestors of multiple events, we want to append to the same HashSet.
	 *
	 * @param ancestors
	 * 		the HashSet to add ancestors to
	 * @param event
	 * 		the event to find ancestors of
	 * @param predicate
	 * 		determines whether or not to add the ancestor to the return list
	 */
	private void findAncestors(
			final HashSet<ShadowEvent> ancestors,
			final ShadowEvent event,
			final Predicate<ShadowEvent> predicate) {
		Deque<ShadowEvent> todoStack = new ArrayDeque<>();

		ShadowEvent sp = event.getSelfParent();
		if (sp != null) {
			todoStack.push(sp);
		}

		ShadowEvent op = event.getOtherParent();
		if (op != null) {
			todoStack.push(op);
		}

		// perform a depth first search of self and other parents
		while (!todoStack.isEmpty()) {
			ShadowEvent x = todoStack.pop();
			if (!expired(x.getEvent()) && predicate.test(x) && ancestors.add(x)) {
				ShadowEvent xsp = x.getSelfParent();
				if (xsp != null) {
					todoStack.push(xsp);
				}
				ShadowEvent xop = x.getOtherParent();
				if (xop != null) {
					todoStack.push(xop);
				}
			}
		}
	}

	/**
	 * <p>Update the reservable generation and remove any events from the shadow graph that can and should be
	 * expired.</p>
	 *
	 * <p>Events that should be expired have a generation that is less than {@code expireBelow}.</p>
	 * <p>Events that are allowed to be expired are events:</p>
	 * <ol>
	 *     <li>whose generation has zero reservations</li>
	 *     <li>whose generation is less than the smallest generation with a non-zero number of reservations</li>
	 * </ol>
	 *
	 * @param generation
	 * 		The generation below which all generations should be expired. For example, if {@code generation}
	 * 		is 100, events in generation 99 and below should be expired.
	 */
	public synchronized void expireBelow(final long generation) {
		if (generation < expireBelow) {
			LOG.error(EXCEPTION.getMarker(),
					"A request to expire generations below {} is less than request of {}. Ignoring expiration request",
					generation, expireBelow);
			// The value of expireBelow must never decrease, so if we receive an invalid request like this, ignore it
			return;
		}

		// Update the smallest generation that should not be expired
		expireBelow = generation;

		// Remove reservations for generations that can and should be expired, and
		// keep track of the oldest generation that can be expired
		long oldestReservedGen = pruneReservationList();

		if (oldestReservedGen == NO_GENERATION_RESERVED) {
			oldestReservedGen = expireBelow;
		}

		stats.updateGensWaitingForExpiry(expireBelow - oldestReservedGen);

		long minGenToKeep = Math.min(expireBelow, oldestReservedGen);

		while (oldestGeneration < minGenToKeep) {
			Set<ShadowEvent> shadowsToExpire = generationToShadowEvent.remove(oldestGeneration);
			// shadowsToExpire should never be null, but check just in case.
			if (shadowsToExpire == null) {
				LOG.error(EXCEPTION.getMarker(), "There were no events in generation {} to expire.", oldestGeneration);
			} else {
				shadowsToExpire.forEach(this::expire);
			}
			oldestGeneration++;
		}
	}

	/**
	 * Removes reservations that can and should be expired, starting with the oldest generation reservation.
	 *
	 * @return the oldest generation with at least one reservation, or {@code -1} if there are no generations with at
	 * 		least one reservation.
	 * @see ShadowGraph#expireBelow
	 */
	private long pruneReservationList() {
		long oldestReservedGen = NO_GENERATION_RESERVED;

		// Iterate through the reservation list in ascending generation order, removing reservations for generations
		// that can and should be expired.
		Iterator<GenerationReservationImpl> iter = reservationList.iterator();
		while (iter.hasNext()) {
			GenerationReservationImpl reservation = iter.next();
			long reservedGen = reservation.getGeneration();

			if (reservation.getNumReservations() > 0) {
				// As soon as we find a reserved reservedGen, stop
				// iterating because we cannot expire this reservedGen
				oldestReservedGen = reservation.getGeneration();
				break;
			} else if (reservedGen < expireBelow) {
				// If the number of reservations is 0 and the
				// reservedGen should be expired, remove the reservation
				iter.remove();
			} else {
				// If the expireBelow reservedGen is reached, stop
				// because no more generations should be expired
				break;
			}
		}
		return oldestReservedGen;
	}

	/**
	 * Expires a single {@link ShadowEvent} from the shadow graph.
	 *
	 * @param shadow
	 * 		the shadow event to expire
	 */
	private void expire(final ShadowEvent shadow) {
		// Remove the shadow from the shadow graph
		hashToShadowEvent.remove(shadow.getEventBaseHash());
		// Remove references to parent shadows so this event gets garbage collected
		shadow.disconnect();
		tips.remove(shadow);
	}

	/**
	 * Get the shadow event that references a hashgraph event instance.
	 *
	 * @param e
	 * 		The event.
	 * @return the shadow event that references an event, or null is {@code e} is null
	 */
	public synchronized ShadowEvent shadow(final Event e) {
		if (e == null) {
			return null;
		}

		return hashToShadowEvent.get(e.getBaseHash());
	}

	/**
	 * Get the shadow events that reference the hashgraph event instances
	 * with the given hashes.
	 *
	 * @param hashes
	 * 		The event hashes to get shadow events for
	 * @return the shadow events that reference the events with the given hashes
	 */
	public synchronized List<ShadowEvent> shadows(final List<Hash> hashes) {
		Objects.requireNonNull(hashes);
		List<ShadowEvent> shadows = new ArrayList<>(hashes.size());
		for (Hash hash : hashes) {
			shadows.add(shadow(hash));
		}
		return shadows;
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
			return shadow.getEvent();
		}
	}

	/**
	 * Returns a copy of the tips at the time of invocation. The returned list is not affected by changes
	 * made to the tip set.
	 *
	 * @return an unmodifiable copy of the tips
	 */
	public synchronized List<ShadowEvent> getTips() {
		return new ArrayList<>(tips);
	}

	/**
	 * If Event `e` is insertable, then insert it and update the tip set, else do nothing.
	 *
	 * @param e
	 * 		The event reference to insert.
	 * @return true iff e was inserted
	 * @throws ShadowGraphInsertionException
	 * 		if the event was unable to be added to the shadow graph
	 */
	public synchronized boolean addEvent(final EventImpl e) throws ShadowGraphInsertionException {
		final InsertableStatus status = insertable(e);

		if (status == InsertableStatus.INSERTABLE) {
			final int tipsBefore = tips.size();
			final ShadowEvent s = insert(e);
			tips.add(s);
			tips.remove(s.getSelfParent());

			if (numberOfNodes > 0 && tips.size() > numberOfNodes && tips.size() > tipsBefore) {
				// It is possible that we have more tips than nodes even if there is no fork.
				// Explained in: sync-protocol.md
				LOG.info(SYNC_INFO.getMarker(),
						"tips size is {} after adding {}. Esp null:{} Ssp null:{}\n" +
								"expireBelow: {} oldestGeneration: {}\n" +
								"current tips:{}",
						tips::size,
						() -> EventStrings.toMediumString(e),
						() -> e.getSelfParent() == null,
						() -> s.getSelfParent() == null,
						() -> expireBelow,
						() -> oldestGeneration,
						() -> tips.stream()
								.map(sh -> EventStrings.toShortString(sh.getEvent()))
								.collect(Collectors.joining(",")));
			}

			return true;
		} else {
			// Every event received should be insertable, so throw an exception if that is not the case
			if (status == InsertableStatus.EXPIRED_EVENT) {
				throw new ShadowGraphInsertionException(
						String.format("`addEvent`: did not insert, status is %s for event %s, oldestGeneration = %s",
								status,
								EventStrings.toMediumString(e),
								oldestGeneration),
						status);
			} else if (status == InsertableStatus.NULL_EVENT) {
				throw new ShadowGraphInsertionException(
						String.format("`addEvent`: did not insert, status is %s", status),
						status);
			} else {
				throw new ShadowGraphInsertionException(
						String.format("`addEvent`: did not insert, status is %s for event %s, oldestGeneration = %s",
								status, EventStrings.toMediumString(e), oldestGeneration),
						status);
			}
		}
	}

	private GenerationReservationImpl newReservation() {
		GenerationReservationImpl reservation = new GenerationReservationImpl(expireBelow);
		reservationList.addLast(reservation);
		return reservation;
	}

	private ShadowEvent shadow(final Hash h) {
		return hashToShadowEvent.get(h);
	}

	/**
	 * Attach a shadow of a Hashgraph event to this graph. Only a shadow for which a parent
	 * hash matches a hash in this@entry is inserted.
	 *
	 * @param e
	 * 		The Hashgraph event shadow to be inserted
	 * @return the inserted shadow event
	 */
	private ShadowEvent insert(final EventImpl e) {
		final ShadowEvent sp = shadow(e.getSelfParent());
		final ShadowEvent op = shadow(e.getOtherParent());

		ShadowEvent se = new ShadowEvent(e, sp, op);

		hashToShadowEvent.put(se.getEventBaseHash(), se);

		if (!generationToShadowEvent.containsKey(e.getGeneration())) {
			generationToShadowEvent.put(e.getGeneration(), new HashSet<>());
		}
		generationToShadowEvent.get(e.getGeneration()).add(se);

		return se;
	}

	/**
	 * Predicate to determine if an event has expired.
	 *
	 * @param event
	 * 		The event.
	 * @return true iff the given event is expired
	 */
	private boolean expired(final Event event) {
		return event.getGeneration() < oldestGeneration;
	}


	/**
	 * Determine whether an event is insertable at time of call.
	 *
	 * @param e
	 * 		The event to evaluate
	 * @return An insertable status, indicating whether the event can be inserted, and if not, the reason it can not be
	 * 		inserted.
	 */
	private InsertableStatus insertable(final EventImpl e) {
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

		// If e has an unexpired parent that is not already referenced by the shadow graph, then we log an error. This
		// is only a sanity check, so there is no need to prevent insertion
		if (hasOP) {
			final boolean knownOP = shadow(e.getOtherParent()) != null;
			final boolean expiredOP = expired(e.getOtherParent());
			if (!knownOP && !expiredOP) {
				LOG.error(EXCEPTION.getMarker(), "Missing non-expired other parent for {}", e::toMediumString);
			}
		}

		if (hasSP) {
			final boolean knownSP = shadow(e.getSelfParent()) != null;
			final boolean expiredSP = expired(e.getSelfParent());
			if (!knownSP && !expiredSP) {
				LOG.error(EXCEPTION.getMarker(), "Missing non-expired self parent for {}", e::toMediumString);
			}
		}

		// If both parents are null, then insertion is allowed. This will create
		// a new tree in the forest view of the graph.
		return InsertableStatus.INSERTABLE;
	}
}



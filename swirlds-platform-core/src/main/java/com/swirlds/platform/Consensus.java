/*
 * (c) 2016-2022 Swirlds, Inc.
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
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.components.OldEventChecker;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.RoundNumberProvider;

import java.util.List;
import java.util.Queue;

/**
 * An interface for classes that calculate consensus of events
 */
public interface Consensus extends OldEventChecker, GraphGenerations, RoundNumberProvider {
	/**
	 * Adds an event to the consensus object. This should be the only public method that modifies the state of the
	 * object.
	 *
	 * @param event
	 * 		the event to be added
	 * @param addressBook
	 * 		the address book to be used
	 * @return A list of consensus events, or null if no consensus was reached
	 */
	List<EventImpl> addEvent(EventImpl event, AddressBook addressBook);

	/**
	 * Get an array of all the events in the hashgraph.
	 *
	 * @return An array of events
	 */
	EventImpl[] getAllEvents();

	/**
	 * Gets a queue of stale events.
	 *
	 * <p>
	 * Stale events do not reach consensus, and their transactions are never handled. The agreement is guaranteed: if
	 * one computer decides that an event is stale, then any other computer that has that event will eventually agree
	 * that it’s stale.
	 * And some computers might never receive the event at all. If one computer decides an event is “consensus”, then
	 * any other computer with that event will eventually decide that it is consensus. And every computer will either
	 * receive that event or will do a reconnect where it receives a state that includes the effects of handling that
	 * event.
	 *
	 * Unless a computer is disconnected from the internet, all its events will be consensus. The only way to be
	 * stale is if you create an event and then it somehow doesn’t get gossipped to the rest of the network for many
	 * rounds. That only happens if you crash, or have an internet connection problem.
	 * </p>
	 *
	 * @return a queue of stale Events
	 */
	Queue<EventImpl> getStaleEventQueue();

	/**
	 * Returns a list of 3 lists of hashes of witnesses associated with round "round".
	 *
	 * The list contains 3 lists. The first is the hashes of the famous witnesses in round "round".
	 * The second is the hashes of witnesses in round "round"-1 which are ancestors of those in the first
	 * list. The third is the hashes of witnesses in round "round"-2 which are ancestors of those in
	 * the first list.
	 *
	 * @param round
	 * 		the 3 lists are rounds round, round-1, round-2
	 * @return the list of 3 lists of hashes of witnesses (famous for round "round", and ancestors of those)
	 */
	List<List<Hash>> getWitnessHashes(final long round);
}

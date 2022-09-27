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

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.consensus.RoundNumberProvider;
import com.swirlds.platform.internal.EventImpl;

import java.util.List;
import java.util.Queue;

/**
 * An interface for classes that calculate consensus of events
 */
public interface Consensus extends GraphGenerations, RoundNumberProvider {
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

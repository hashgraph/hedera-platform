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

import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.internal.CreatorSeqPair;

import java.util.List;
import java.util.Queue;

/**
 * An interface for classes that calculate consensus of events
 */
public interface Consensus {
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
	 * return the round number of the latest round for which the fame of all witnesses has been decided for
	 * that round and all earlier rounds.
	 *
	 * @return the round number
	 */
	long getLastRoundDecided();

	/**
	 * Return the max round number for which we have an event. If there are none yet, return -1.
	 *
	 * @return the max round number, or -1 if none.
	 */
	long getMaxRound();

	/**
	 * Return the minimum round number for which we have an event. If there are none yet, return -1.
	 *
	 * @return the minimum round number, or -1 if none.
	 */
	long getMinRound();

	/**
	 * Return the minimum generation of all the famous witnesses that are not in ancient rounds.
	 *
	 * <p>Define gen(R) to be the minimum generation of all the events that were famous witnesses in round R.
	 *
	 * If round R is the most recent round for which we have decided the fame of all the witnesses, then any event with
	 * a generation less than gen(R - {@code Settings.state.roundsExpired}) is called an “expired” event. And any
	 * non-expired event with a generation less than gen(R - {@code Settings.state.roundsStale}) is an “ancient” event.
	 * If the event failed to achieve consensus before becoming ancient, then it is “stale”. So every non-expired event
	 * with a generation before gen(R - {@code Settings.state.roundsStale}) is either stale or consensus, not both.
	 *
	 * Expired events can be removed from memory unless they are needed for an old signed state that is still being used
	 * for something (such as still in the process of being written to disk).
	 * </p>
	 *
	 * @return the minimum generation
	 */
	long getMinGenerationNonAncient();


	/**
	 * @return the number of events in the hashgraph
	 */
	int getNumEvents();

	/**
	 * Get an array of all the events in the hashgraph.
	 *
	 * @return An array of events
	 */
	EventImpl[] getAllEvents();

	/**
	 * Get an array of all the events in the hashgraph with generation number
	 * &gt;= `minGenerationNumber`
	 *
	 * @param minGenerationNumber
	 * 		the minimum generation to identify in the hashgraph
	 * @return An array of events
	 */
	EventImpl[] getRecentEvents(long minGenerationNumber);

	/**
	 * get an event, given the ID of its creator, and the sequence number, where that creator's first event
	 * is seq==0, the next is seq==1 and so on. Return null if it doesn't exist.
	 *
	 * @param pair
	 * 		the creator sequence pair that identifies this event
	 * @return the Event, or null if it doesn't exist.
	 */
	EventImpl getEvent(CreatorSeqPair pair);

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

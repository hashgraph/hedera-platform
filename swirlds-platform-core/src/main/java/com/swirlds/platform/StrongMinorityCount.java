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
import com.swirlds.common.events.Event;

import java.util.concurrent.atomic.AtomicIntegerArray;

class StrongMinorityCount implements StrongMinority {

	// ----------------------------------------------------------------------------------------------------------------
	// --- START ecMax | variables related to number of events created in the latest round (maxRound)
	/** An array that tracks how many event each member created in the most recent round */
	private AtomicIntegerArray ecMaxByMemberIndex;
	/**
	 * Tracks how many members have created at least a certain number of events in the latest round. The 0 index
	 * represents how many members have created 0 events (everybody), index 1 represents how many members created at
	 * least one event, and so on.
	 */
	private AtomicIntegerArray ecMaxByEventNumber;
	/** The round for which we are tracking events created by each member */
	private long ecMaxRound = -1;
	/** The maximum number of events created by the super minority in the latest round */
	private int ecMaxSupMinThreshold = 0;
	/** The size of the ecMaxByEventNumber array */
	private static final int EC_MAX_EVENT_NUMBER_SIZE = 100;
	// --- END ecMax | variables related to number of events created in the latest round (maxRound)
	// ----------------------------------------------------------------------------------------------------------------

	private Consensus consensus;

	public StrongMinorityCount(final Consensus consensus, final AddressBook addressBook) {
		this.consensus = consensus;
		this.ecMaxByMemberIndex = new AtomicIntegerArray(addressBook.getSize());
		this.ecMaxByEventNumber = new AtomicIntegerArray(EC_MAX_EVENT_NUMBER_SIZE);
		this.ecMaxByEventNumber.set(0, addressBook.getSize());
		this.ecMaxSupMinThreshold = 0;
	}

	@Override
	public boolean isStrongMinorityInMaxRound(final long nodeId) {
		return ecMaxByMemberIndex.get((int) nodeId) <= ecMaxSupMinThreshold;
	}

	@Override
	public void update(final AddressBook addressBook, final Event event) {
		if (consensus.getMaxRound() > ecMaxRound) {
			// we have a new round, so we need reset the ecMax variables
			ecMaxRound = consensus.getMaxRound();
			ecMaxSupMinThreshold = 0;
			ecMaxByMemberIndex = new AtomicIntegerArray(addressBook.getSize());
			ecMaxByEventNumber = new AtomicIntegerArray(EC_MAX_EVENT_NUMBER_SIZE);
			ecMaxByEventNumber.set(0, addressBook.getSize());
		}

		int numMembers = addressBook.getSize();
		// if we have a new event in the latest round, update the ecMax variables
		if (event.getRoundCreated() == consensus.getMaxRound()) {
			// increment the number of events created by the event creator
			int numEvIndex = ecMaxByMemberIndex.incrementAndGet((int) event.getCreatorId());
			// if this node created more events than we are counting, we don't have to do anything. This should not
			// happen ordinarily
			if (numEvIndex < EC_MAX_EVENT_NUMBER_SIZE) {
				// we then increment the object that keeps track of how many nodes have created at least a certain
				// number of events. When a node creates its first event, the index 1 will be incremented. When it
				// creates a second event, it will increment the index 2, while index 1 will remain the same. If a
				// node created 2 events, it still means it created at least 1.
				int numMemInIndex = ecMaxByEventNumber.incrementAndGet(numEvIndex);
				// we calculate the super minority, which is either exactly one third, or slightly more
				//int supMinority = (numMembers + 2) / 3;
				// we need to calculate the super minority threshold, which is defined by how many events nodes have
				// created. SupMinThreshold means that a node is part of the super minority if it has created this
				// many or fewer events.
				// the SupMinThreshold will start from zero, then rise as more nodes create events. At this point we
				// know that numMemInIndex has incremented, so we need to check if it affects our threshold.
				// | ecMaxSupMinThreshold < numEvIndex | if numMemInIndex refers to a lower value, this does not
				// concern us as we want the highest value that contains a super minority.
				// in practice, numEvIndex can be at most ecMaxSupMinThreshold+1
				// | numMemInIndex > numMembers - supMinority | we check if enough nodes have created this many events
				// (numMemInIndex) that we can move the SupMinThreshold to this value. If the number of members is
				// bigger than numMembers - supMinority, that means that it contains part of the super minority, so we
				// move the threshold to that value
				//
				// The following used to be written in terms of a superminority, but that is mathematically equivalent
				// to the following, which is written in terms of a supermajority.
				if (ecMaxSupMinThreshold < numEvIndex && Utilities.isSupermajority(numMemInIndex, numMembers)) {
					ecMaxSupMinThreshold = numEvIndex;
				}
			}
		}
	}
}

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
import java.util.concurrent.atomic.AtomicLongArray;

class StrongMinorityStake implements StrongMinority {

	// ----------------------------------------------------------------------------------------------------------------
	// --- START ecMax | variables related to number of events created in the latest round (maxRound)
	/** An array that tracks how many event each member created in the most recent round */
	private AtomicIntegerArray ecMaxByMemberIndex;
	/**
	 * Tracks the total stake of the members have created at least a certain number of events in the latest round. The 0
	 * index
	 * represents the stake of the members that have created 0 events (everybody), index 1 represents the stake of the
	 * members that have created at least one event, and so on.
	 */
	private AtomicLongArray ecMaxByEventNumber;
	/** The round for which we are tracking events created by each member */
	private long ecMaxRound = -1;
	/** The maximum number of events created by the super minority in the latest round */
	private int ecMaxSupMinThreshold = 0;
	/** The size of the ecMaxByEventNumber array */
	private static final int EC_MAX_EVENT_NUMBER_SIZE = 100;
	// --- END ecMax | variables related to number of events created in the latest round (maxRound)
	// ----------------------------------------------------------------------------------------------------------------

	private Consensus consensus;

	public StrongMinorityStake(final Consensus consensus, final AddressBook addressBook) {
		this.consensus = consensus;
		this.ecMaxSupMinThreshold = 0;
		this.ecMaxByMemberIndex = new AtomicIntegerArray(addressBook.getSize());
		this.ecMaxByEventNumber = new AtomicLongArray(EC_MAX_EVENT_NUMBER_SIZE);
		this.ecMaxByEventNumber.set(0, addressBook.getTotalStake());
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
			ecMaxByEventNumber = new AtomicLongArray(EC_MAX_EVENT_NUMBER_SIZE);
			ecMaxByEventNumber.set(0, addressBook.getTotalStake());
		}

		// if we have a new event in the latest round, update the ecMax variables
		if (event.getRoundCreated() == consensus.getMaxRound()) {
			// increment the number of events created by the event creator
			int numEvIndex = ecMaxByMemberIndex.incrementAndGet((int) event.getCreatorId());
			// if this node created more events than we are counting, we don't have to do anything. This should not
			// happen ordinarily
			if (numEvIndex < EC_MAX_EVENT_NUMBER_SIZE) {
				// we then increment the object that keeps track of the stake of the nodes that have created at least
				// a certain number of events. When a node creates its first event, the index 1 will be have the nodes
				// stake added to it. When it creates a second event, it will add the nodes stake to index 2, while
				// index 1 will remain the same. If a node created 2 events, it still means it created at least 1.
				long stakeInIndex = ecMaxByEventNumber.addAndGet(numEvIndex,
						addressBook.getStake(event.getCreatorId()));
				// we calculate the strong minority, which is either exactly one third, or slightly more
				// int strongMinority = whole / 3 + (whole % 3 == 0 ? 0 : 1);
				// we need to calculate the strong minority threshold, which is defined by the total stake of the
				// nodes that have created a given number of events.
				// The ecSupMinThreshold will start from zero, then rise as more nodes create events. At this point we
				// know that stakeInIndex has increased, so we need to check if it affects our threshold.
				// | ecMaxSupMinThreshold < numEvIndex | if numMemInIndex refers to a lower value, this does not
				// concern us as we want the highest value that contains a super minority.
				// in practice, numEvIndex can be at most ecMaxSupMinThreshold+1
				// | numMemInIndex > numMembers - supMinority | we check if we have a strong minority in (stakeInIndex)
				// when compared to the (totalStake) so that we can move the SupMinThreshold to this value. If the
				// stake at numEvIndex is at least 1/3 of the total stake, then that means that it contains part of the
				// super minority, so we move the threshold to that value.
				if (numEvIndex > ecMaxSupMinThreshold && Utilities.isStrongMinority(stakeInIndex,
						addressBook.getTotalStake())) {
					ecMaxSupMinThreshold = numEvIndex;
				}
			}
		}

	}

}

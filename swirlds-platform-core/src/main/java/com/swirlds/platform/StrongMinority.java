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

/**
 * Encapsulates the {@link #isStrongMinorityInMaxRound(long)} logic from {@link Hashgraph} into a reusable interface for
 * the purposes of being able to easily change implementations and unit test.
 */
public interface StrongMinority {

	/**
	 * Checks whether the node with the supplied id is in the super minority by number of events created in the most
	 * recent round.
	 *
	 * @param nodeId
	 * 		the id of the node to check
	 * @return true if it is in the super minority, false otherwise
	 */
	boolean isStrongMinorityInMaxRound(long nodeId);

	/**
	 * Called for each event creation to recalculate the strong minority.
	 *
	 * @param addressBook
	 * 		the latest copy of the address book
	 * @param event
	 * 		the event that was just created
	 */
	void update(AddressBook addressBook, Event event);

}

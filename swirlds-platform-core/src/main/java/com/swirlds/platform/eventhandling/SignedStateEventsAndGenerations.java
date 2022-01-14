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

package com.swirlds.platform.eventhandling;

import com.swirlds.platform.EventImpl;

import java.util.List;

/**
 * Stores events and round information that is needed for the SignedState
 *
 * NOTE: This class duplicates some logic that is contained within EventFlow. The plan is for this class to replace that
 * logic once the refactoring of EventFlow starts.
 */
public class SignedStateEventsAndGenerations {
	private final int roundsAncient;
	private final SignedStateEventStorage events;
	private final MinGenQueue generations;

	private long lastRoundReceived = 0;

	public SignedStateEventsAndGenerations(final int roundsAncient, final int numMembers) {
		this.roundsAncient = roundsAncient;
		events = new SignedStateEventStorage(numMembers);
		generations = new MinGenQueue();
	}

	public void addEvents(final List<EventImpl> events) {
		this.events.add(events);
		lastRoundReceived = events.get(events.size() - 1).getRoundReceived();
	}

	public void addEvent(final EventImpl event) {
		this.events.add(event);
		lastRoundReceived = event.getRoundReceived();
	}

	public void addRoundGeneration(final long round, final long minGeneration) {
		generations.add(round, minGeneration);
	}

	public void expire() {
		long staleRound = lastRoundReceived - roundsAncient;
		// if we dont have stale rounds yet, no need to do anything
		/* starting round is now 1, so anything less than one will be discounted */
		if (staleRound < 1) {
			return;
		}

		// we need to keep all events that are non-ancient
		final long minGenerationNonAncient = generations.getRoundGeneration(staleRound);
		events.expireEvents(minGenerationNonAncient);
		// sometimes we will have events that have a round created lower than staleRound, so we need to keep more than
		// the specified number of rounds. At other times, we might have round generation numbers that are not
		// increasing, so we might have no events in the staleRound.
		// we want to keep at least roundsAncient number of rounds, sometimes more
		final long expireRoundsBelow = Math.min(staleRound, events.getMinRoundCreatedInQueue());
		generations.expire(expireRoundsBelow);

	}
}

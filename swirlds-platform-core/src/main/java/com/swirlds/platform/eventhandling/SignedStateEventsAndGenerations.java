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
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.StateSettings;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Stores events and round information that is needed for the SignedState
 *
 * NOTE: This class duplicates some logic that is contained within EventFlow. The plan is for this class to replace that
 * logic once the refactoring of EventFlow starts.
 */
public class SignedStateEventsAndGenerations {
	private final StateSettings settings;
	private final SignedStateEventStorage events;
	private final MinGenQueue generations;

	public SignedStateEventsAndGenerations(final StateSettings settings) {
		this.settings = settings;
		events = new SignedStateEventStorage();
		generations = new MinGenQueue();
	}

	public long getLastRoundReceived() {
		return events.getLatestRoundReceived();
	}

	public void addEvents(final List<EventImpl> events) {
		if (events.isEmpty()) {
			return;
		}
		this.events.add(events);
	}

	public void addEvent(final EventImpl event) {
		this.events.add(event);
	}

	public int getNumberOfEvents() {
		return events.getQueueSize();
	}

	public void addRoundGeneration(final long round, final long minGeneration) {
		generations.add(round, minGeneration);
	}

	public void expire() {
		if (events.getQueueSize() == 0) {
			//nothing to expire
			return;
		}
		// the round whose min generation we need
		final long oldestNonAncientRound = settings.getOldestNonAncientRound(getLastRoundReceived());

		// we need to keep all events that are non-ancient
		final long minGenerationNonAncient = generations.getRoundGeneration(oldestNonAncientRound);
		events.expireEvents(minGenerationNonAncient);
		// sometimes we will have events that have a round created lower than oldestNonAncientRound, so we need to keep
		// more than the specified number of rounds. At other times, we might have round generation numbers that are not
		// increasing, so we might have no events in the oldestNonAncientRound.
		// we want to keep at least roundsNonAncient number of rounds, sometimes more
		final long expireRoundsBelow = Math.min(oldestNonAncientRound, events.getMinRoundCreatedInQueue());
		generations.expire(expireRoundsBelow);
	}

	public EventImpl[] getEventsForSignedState() {
		return events.getEventsForLatestRound();
	}

	public List<Pair<Long, Long>> getMinGenForSignedState() {
		return generations.getList(getLastRoundReceived());
	}

	public void clear() {
		events.clear();
		generations.clear();
	}

	public void loadDataFromSignedState(final SignedState signedState) {
		generations.addAll(signedState.getMinGenInfo());

		final long minGenNonAncient = settings.getMinGenNonAncient(
				signedState.getLastRoundReceived(), generations::getRoundGeneration
		);
		events.loadDataFromSignedState(signedState.getEvents(), minGenNonAncient);
	}
}

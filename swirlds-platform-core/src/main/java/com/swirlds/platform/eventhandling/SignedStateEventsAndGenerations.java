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

package com.swirlds.platform.eventhandling;

import com.swirlds.platform.EventImpl;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.StateSettings;

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

	public List<MinGenInfo> getMinGenForSignedState() {
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

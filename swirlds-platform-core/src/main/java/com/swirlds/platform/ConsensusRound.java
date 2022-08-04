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

import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

/**
 * A consensus round with all its events.
 */
public class ConsensusRound {

	/** the consensus events in this round, in consensus order */
	private final List<EventImpl> consensusEvents;

	/** the consensus generations when this round reached consensus */
	private final GraphGenerations generations;

	/** this round's number */
	private final long roundNum;

	/** the last event in the round */
	private EventImpl lastEvent;

	/** true if this round contains a shutdown event */
	private boolean hasShutdownEvent;

	/**
	 * Create a new instance with the provided consensus events.
	 *
	 * @param consensusEvents
	 * 		the events in the round, in consensus order
	 * @param generations
	 * 		the consensus generations for this round
	 */
	public ConsensusRound(final List<EventImpl> consensusEvents, final GraphGenerations generations) {
		this.consensusEvents = consensusEvents;
		this.generations = generations;

		for (final EventImpl e : consensusEvents) {
			if (e.isLastOneBeforeShutdown()) {
				hasShutdownEvent = true;
			}
		}

		final EventImpl lastInList = consensusEvents.get(consensusEvents.size() - 1);
		if (lastInList.isLastInRoundReceived()) {
			lastEvent = lastInList;
		}

		this.roundNum = consensusEvents.get(0).getRoundReceived();
	}

	/**
	 * @return true if this round is complete (contains the last event of the round)
	 */
	public boolean isComplete() {
		return lastEvent != null;
	}

	/**
	 * @return the list of events in this round
	 */
	public List<EventImpl> getConsensusEvents() {
		return consensusEvents;
	}

	/**
	 * @return the consensus generations when this round reached consensus
	 */
	public GraphGenerations getGenerations() {
		return generations;
	}

	/**
	 * @return the number of events in this round
	 */
	public int getNumEvents() {
		return consensusEvents.size();
	}

	/**
	 * @return this round's number
	 */
	public long getRoundNum() {
		return roundNum;
	}

	/**
	 * @return the last event of this round, or null if this round is not complete
	 */
	public EventImpl getLastEvent() {
		return lastEvent;
	}

	/**
	 * @return true if this round contains a shutdown event
	 */
	public boolean hasShutdownEvent() {
		return hasShutdownEvent;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final ConsensusRound round = (ConsensusRound) o;

		return new EqualsBuilder()
				.append(consensusEvents, round.consensusEvents)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(consensusEvents)
				.toHashCode();
	}

	@Override
	public String toString() {
		return "round: " + roundNum + ", consensus events: " + EventUtils.toShortStrings(consensusEvents);
	}


}

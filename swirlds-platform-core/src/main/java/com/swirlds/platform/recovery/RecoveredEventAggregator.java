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

package com.swirlds.platform.recovery;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.sync.Generations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;

/**
 * <p>
 * Collects events one by one and aggregates them into rounds. Completed rounds are forwarded to a consumer. A single
 * complete round is buffered in this class before being sent to the consumer.
 * </p>
 *
 * <p>
 * This class is used by the recovery process to aggregate events read from the event stream that need to be handled. An
 * entire round is buffered so that once all events are aggregated, the last complete round number is known. Recovery
 * needs this number so that it knows to shut down when a signed state is written to disk for this round.
 * </p>
 */
public class RecoveredEventAggregator {

	private static final Logger LOG = LogManager.getLogger();
	private final Consumer<ConsensusRound> roundConsumer;
	private final BiConsumer<Long, Long> minGenConsumer;
	/** Stores the current round events */
	private RoundWorkingData workingData;
	/** Stores the latest complete round of events */
	private RoundWorkingData lastCompleteRound;
	/** The final complete round after all events have been aggregated */
	private long finalCompleteRound = 0;
	/** The total number of events aggregated */
	private int totalEvents;
	/** The total number of rounds, complete and incomplete */
	private int totalRounds;

	/**
	 * @param roundConsumer
	 * 		consumer of the completed rounds
	 * @param minGenConsumer
	 * 		consumer of the min gen for the complete round
	 */
	public RecoveredEventAggregator(
			final Consumer<ConsensusRound> roundConsumer,
			final BiConsumer<Long, Long> minGenConsumer) {
		throwArgNull(roundConsumer, "roundConsumer");
		throwArgNull(minGenConsumer, "minGenConsumer");
		this.roundConsumer = roundConsumer;
		this.minGenConsumer = minGenConsumer;
	}

	/**
	 * Add an event to the aggregator. Events must be added in consensus order.
	 *
	 * Once an event from the third round is received, the first round will be sent to the {@code roundConsumer}.
	 * Calling {@link #sendLastRound()} sends the remaining rounds.
	 *
	 * @param event
	 * 		the event to add
	 */
	public void addEvent(final EventImpl event) {
		totalEvents++;
		if (workingData == null) {
			newWorkingRound(event);
		} else if (event.getRoundReceived() > workingData.roundNum) {
			sendLastCompleteRound();
			completeWorkingRound();
			newWorkingRound(event);
		} else if (event.getRoundReceived() == workingData.roundNum) {
			workingData.addEvent(event);
		} else {
			LOG.error(EVENT_PARSER.getMarker(),
					"Received event {} for invalid round {}. Current working round is {}, last complete round is {}",
					event::toMediumString,
					event::getRoundReceived,
					() -> workingData.roundNum,
					() -> lastCompleteRound.roundNum);
		}
	}

	private void newWorkingRound(final EventImpl event) {
		workingData = new RoundWorkingData(event.getRoundReceived());
		workingData.addEvent(event);
	}

	private void completeWorkingRound() {
		if (!workingData.events.isEmpty()) {
			workingData.events.sort(new EventComparator());
			lastCompleteRound = workingData;
		}
	}

	private static class EventComparator implements Comparator<EventImpl> {
		@Override
		public int compare(final EventImpl o1, final EventImpl o2) {
			return Long.compare(o1.getConsensusOrder(), o2.getConsensusOrder());
		}
	}

	private void sendLastCompleteRound() {
		if (lastCompleteRound != null) {
			minGenConsumer.accept(lastCompleteRound.roundNum, lastCompleteRound.minGen);
			roundConsumer.accept(new ConsensusRound(
					lastCompleteRound.events,
					Generations.GENESIS_GENERATIONS // unused by recovery
			));
			totalRounds++;
		}
	}

	/**
	 * Provides the round number of the last complete round (the last round in which the last event has
	 * {@link EventImpl#isLastInRoundReceived()} set to {@code true}).
	 *
	 * @return the last round number
	 */
	public long getLastCompleteRound() {
		return finalCompleteRound;
	}

	/**
	 * Provides the total number of events aggregated, whether in a complete or incomplete round.
	 *
	 * @return the total number of events
	 */
	public int getTotalEvents() {
		return totalEvents;
	}

	/**
	 * Provides the total number of rounds events were aggregated into, whether complete or incomplete.
	 *
	 * @return the total number of rounds
	 */
	public int getTotalRounds() {
		return totalRounds;
	}

	/**
	 * Indicates no more events will be added for aggregation. Determines the last complete rounds of events.
	 *
	 * @return the last complete round of events
	 */
	public long noMoreEvents() {
		final EventImpl lastEvent = workingData.getLastEvent();
		if (lastEvent == null) {
			LOG.warn(EVENT_PARSER.getMarker(), "No recovered events were received by the event aggregator.");
			return 0L;
		}

		if (!lastEvent.isLastInRoundReceived()) {
			LOG.info(EVENT_PARSER.getMarker(), "Last recovered event is not last event of its round ({})",
					lastEvent.getRoundReceived());
			if (lastCompleteRound == null) {
				LOG.error(EVENT_PARSER.getMarker(), "Fewer than a round of events were recovered. " +
						"Unable to determine the shutdown signed state round.");
			} else {
				finalCompleteRound = lastCompleteRound.roundNum;
			}
		} else {
			finalCompleteRound = workingData.roundNum;
		}

		// force the event stream to shut down after writing this event
		lastEvent.setLastOneBeforeShutdown(true);

		LOG.info(EVENT_PARSER.getMarker(), "Last complete recovered round: {}", finalCompleteRound);

		return finalCompleteRound;
	}

	/**
	 * Sends the last round(s) to the consumer. Should be called after {@link #noMoreEvents()}.
	 */
	public void sendLastRound() {
		sendLastCompleteRound();
		completeWorkingRound();
		sendLastCompleteRound();
	}

	private static class RoundWorkingData {
		final long roundNum;
		long minGen;
		List<EventImpl> events;

		RoundWorkingData(final long roundNum) {
			this.roundNum = roundNum;
			this.minGen = Long.MAX_VALUE;
			events = new ArrayList<>();
		}

		public EventImpl getLastEvent() {
			if (events.isEmpty()) {
				return null;
			}
			return events.get(events.size() - 1);
		}

		public void addEvent(final EventImpl event) {
			events.add(event);
			minGen = Math.min(minGen, event.getGeneration());
		}
	}

}

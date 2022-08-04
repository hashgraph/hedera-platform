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

package com.swirlds.platform.intake;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.stats.CycleTimingStat;
import org.apache.commons.lang3.tuple.Pair;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Statistics that track time spent in various phases of event intake
 */
public class IntakeStats {
	private final List<Pair<String, String>> unlinkedPhases = List.of(
			Pair.of("received", "time dispatch received event (in micros)"),
			Pair.of("linking", "time link the event to its parents (in micros)"),
			Pair.of("adding", "time add all linked events (in micros)")
	);
	private final CycleTimingStat unlinkedEventTiming = new CycleTimingStat(
			ChronoUnit.MICROS,
			"intake",
			"intake-unlinked",
			unlinkedPhases.size(),
			unlinkedPhases.stream().map(Pair::getLeft).collect(Collectors.toList()),
			unlinkedPhases.stream().map(Pair::getRight).collect(Collectors.toList())
	);

	private final List<Pair<String, String>> intakePhases = List.of(
			Pair.of("validation", "time of second phase of event validation (in micros)"),
			Pair.of("preConsensus", "time dispatch pre-consensus event (in micros)"),
			Pair.of("consensus", "time add event to consensus (in micros)"),
			Pair.of("added", "time dispatch event added (in micros)"),
			Pair.of("round", "time dispatch consensus round (in micros)"),
			Pair.of("stale", "time dispatch stale event (in micros)")
	);
	private final CycleTimingStat eventIntakeTiming = new CycleTimingStat(
			ChronoUnit.MICROS,
			"intake",
			"intake-linked",
			intakePhases.size(),
			intakePhases.stream().map(Pair::getLeft).collect(Collectors.toList()),
			intakePhases.stream().map(Pair::getRight).collect(Collectors.toList())
	);

	/** An unlinked event it added to gossip */
	public void receivedUnlinkedEvent() {
		unlinkedEventTiming.startCycle();
	}

	/** An unlinked event has been dispatched as received */
	public void dispatchedReceived() {
		unlinkedEventTiming.setTimePoint(1);
	}

	/** Event linking is done */
	public void doneLinking() {
		unlinkedEventTiming.setTimePoint(2);
	}

	/** Done adding a linked event */
	public void doneAdding() {
		unlinkedEventTiming.stopCycle();
	}

	/** Intake of a linked event is starting */
	public void startIntake() {
		eventIntakeTiming.startCycle();
	}

	/** Linked event validation is done */
	public void doneValidation() {
		eventIntakeTiming.setTimePoint(1);
	}

	/** A linked event has been dispatched before adding to consensus */
	public void dispatchedPreConsensus() {
		eventIntakeTiming.setTimePoint(2);
	}

	/** A linked event has been added to consensus */
	public void addedToConsensus() {
		eventIntakeTiming.setTimePoint(3);
	}

	/** A linked event has been dispatched after adding to consensus */
	public void dispatchedAdded() {
		eventIntakeTiming.setTimePoint(4);
	}

	/** A linked consensus round has been dispatched */
	public void dispatchedRound() {
		eventIntakeTiming.setTimePoint(5);
	}

	/** A linked stale event has been dispatched */
	public void dispatchedStale() {
		eventIntakeTiming.stopCycle();
	}

	public List<StatEntry> getAllEntries() {
		return CommonUtils.joinLists(unlinkedEventTiming.getAllEntries(), eventIntakeTiming.getAllEntries());
	}

}

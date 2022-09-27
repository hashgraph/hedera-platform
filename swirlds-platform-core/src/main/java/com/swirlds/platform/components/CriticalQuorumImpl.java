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

package com.swirlds.platform.components;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.event.EventUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the {@link CriticalQuorum} algorithm.
 */
public class CriticalQuorumImpl implements CriticalQuorum {

	/**
	 * The current address book for this node.
	 */
	private final AddressBook addressBook;

	/**
	 * The number of events observed from each node in the current round.
	 */
	private final Map<Long, Integer> eventCounts;

	/**
	 * A map from possible thresholds to stakes. The given stake is the stake of all
	 * nodes that do not exceed the threshold.
	 */
	private final Map<Integer, Long> stakeNotExceedingThreshold;

	/**
	 * Any nodes with an event count that does not exceed this threshold are considered
	 * to be part of the critical quorum.
	 */
	private final AtomicInteger threshold;

	/**
	 * The current round. Observing an event from a higher round will increase this value and
	 * reset event counts.
	 */
	private long round;

	/**
	 * Construct a critical quorum from an address book
	 *
	 * @param addressBook
	 * 		the source address book
	 */
	public CriticalQuorumImpl(final AddressBook addressBook) {
		this.addressBook = addressBook;

		eventCounts = new ConcurrentHashMap<>();
		stakeNotExceedingThreshold = new HashMap<>();

		threshold = new AtomicInteger(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isInCriticalQuorum(final long nodeId) {
		return eventCounts.getOrDefault(nodeId, 0) <= threshold.get();
	}

	/**
	 * When the round increases we need to reset all of our counts.
	 */
	private void handleRoundBoundary(final EventImpl event) {
		if (event.getRoundCreated() > round) {
			round = event.getRoundCreated();
			eventCounts.clear();
			stakeNotExceedingThreshold.clear();
			threshold.set(0);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		if (event.getRoundCreated() < round) {
			// No need to consider old events
			return;
		}

		handleRoundBoundary(event);

		final long nodeId = event.getCreatorId();
		final long totalState = addressBook.getTotalStake();

		// Increase the event count
		final int originalEventCount = eventCounts.getOrDefault(nodeId, 0);
		eventCounts.put(nodeId, originalEventCount + 1);

		// Update threshold map
		final long originalStakeAtThreshold = stakeNotExceedingThreshold.getOrDefault(originalEventCount, totalState);
		final long newStakeAtThreshold = originalStakeAtThreshold - addressBook.getAddress(nodeId).getStake();
		stakeNotExceedingThreshold.put(originalEventCount, newStakeAtThreshold);

		// Make sure threshold allows at least 1/3 of the stake to be part of the critical quorum
		if (!Utilities.isStrongMinority(stakeNotExceedingThreshold.getOrDefault(threshold.get(), totalState),
				totalState)) {
			threshold.incrementAndGet();
		}
	}

	@Override
	public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
		// if neither node is part of the superMinority in the latest round, don't create an event
		if (!isInCriticalQuorum(EventUtils.getCreatorId(selfParent)) &&
				!isInCriticalQuorum(EventUtils.getCreatorId(otherParent))) {
			return EventCreationRuleResponse.DONT_CREATE;
		}
		return EventCreationRuleResponse.PASS;
	}
}

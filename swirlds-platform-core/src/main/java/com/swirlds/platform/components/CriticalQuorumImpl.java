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

package com.swirlds.platform.components;

import com.swirlds.common.AddressBook;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.Utilities;

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
		final long newStakeAtThreshold = originalStakeAtThreshold - addressBook.getStake(nodeId);
		stakeNotExceedingThreshold.put(originalEventCount, newStakeAtThreshold);

		// Make sure threshold allows at least 1/3 of the stake to be part of the critical quorum
		if (!Utilities.isStrongMinority(stakeNotExceedingThreshold.getOrDefault(threshold.get(), totalState),
				totalState)) {
			threshold.incrementAndGet();
		}
	}
}

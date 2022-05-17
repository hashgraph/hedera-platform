/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.event;

import com.swirlds.common.NodeId;
import com.swirlds.common.threading.StoppableThread;
import com.swirlds.common.threading.StoppableThreadConfiguration;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.sync.SyncResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * An independent thread that creates new events periodically
 */
public class EventCreatorThread {
	private final StoppableThread creatorThread;
	private final List<NodeId> allNeighbors;
	private final RandomGenerator random;
	private final Predicate<SyncResult> creationCheck;
	private final LongConsumer eventCreator;
	private final int createEventSleepMs;

	public EventCreatorThread(
			final NodeId selfId,
			final int attemptedChatterEventPerSecond,
			final NetworkTopology topology,
			final Predicate<SyncResult> creationCheck,
			final LongConsumer eventCreator) {
		this.createEventSleepMs = 1000 / attemptedChatterEventPerSecond;
		this.creationCheck = creationCheck;
		this.eventCreator = eventCreator;
		this.allNeighbors = new ArrayList<>(topology.getNeighbors());
		this.random = CryptoStatic.getNonDetRandom();

		creatorThread = new StoppableThreadConfiguration<>()
				.setPriority(Thread.NORM_PRIORITY)
				.setNodeId(selfId.getId())
				.setComponent("Chatter")
				.setThreadName("EventGenerator")
				.setWork(this::createEvent)
				.build();
	}

	private void createEvent() throws InterruptedException {
		final NodeId otherId = allNeighbors.get(random.nextInt(allNeighbors.size()));
		final SyncResult syncResult = new SyncResult(
				false, // not relevant with Chatter
				otherId,
				0, // not relevant with Chatter
				0 // not relevant with Chatter
		);

		if (this.creationCheck.test(syncResult)) {
			this.eventCreator.accept(otherId.getId());
		}

		Thread.sleep(createEventSleepMs);
	}

	public void start() {
		creatorThread.start();
	}
}

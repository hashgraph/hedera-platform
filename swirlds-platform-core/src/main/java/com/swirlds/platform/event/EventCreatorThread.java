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

package com.swirlds.platform.event;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.network.topology.NetworkTopology;
import com.swirlds.platform.sync.SyncResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * An independent thread that creates new events periodically
 */
public class EventCreatorThread {
	private final StoppableThread creatorThread;
	private final List<NodeId> allNeighbors;
	private final Random random;
	private final Predicate<SyncResult> creationCheck;
	private final LongPredicate eventCreator;

	public EventCreatorThread(
			final NodeId selfId,
			final int attemptedChatterEventPerSecond,
			final NetworkTopology topology,
			final Predicate<SyncResult> creationCheck,
			final LongPredicate eventCreator) {
		this.creationCheck = creationCheck;
		this.eventCreator = eventCreator;
		this.allNeighbors = new ArrayList<>(topology.getNeighbors());
		this.random = CryptoStatic.getNonDetRandom();

		creatorThread = new StoppableThreadConfiguration<>()
				.setPriority(Thread.NORM_PRIORITY)
				.setNodeId(selfId.getId())
				.setMaximumRate(attemptedChatterEventPerSecond)
				.setComponent("Chatter")
				.setThreadName("EventGenerator")
				.setWork(this::createEvent)
				.build();
	}

	private void createEvent() {
		Collections.shuffle(allNeighbors, random);
		for (final NodeId neighbor : allNeighbors) {
			final SyncResult syncResult = new SyncResult(
					false, // not relevant with Chatter
					neighbor,
					0, // not relevant with Chatter
					0 // not relevant with Chatter
			);
			if (this.creationCheck.test(syncResult) && this.eventCreator.test(neighbor.getId())) {
				// try all neighbors until we create an event
				break;
			}
		}
	}

	public void start() {
		creatorThread.start();
	}
}

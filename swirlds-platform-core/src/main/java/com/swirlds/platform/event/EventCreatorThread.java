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
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.utility.BooleanFunction;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.crypto.CryptoStatic;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An independent thread that creates new events periodically
 */
public class EventCreatorThread implements Clearable {
	private final StoppableThread creatorThread;
	private final List<NodeId> otherNodes;
	private final Random random;
	private final BooleanFunction<Long> eventCreator;

	public EventCreatorThread(
			final NodeId selfId,
			final int attemptedChatterEventPerSecond,
			final AddressBook addressBook,
			final BooleanFunction<Long> eventCreator) {
		this.eventCreator = eventCreator;
		this.otherNodes = StreamSupport.stream(addressBook.spliterator(), false)
				// don't create events with self as other parent
				.filter(a -> !selfId.equalsMain(a.getId()))
				.map(a -> NodeId.createMain(a.getId()))
				.collect(Collectors.toList());
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
		Collections.shuffle(otherNodes, random);
		for (final NodeId neighbor : otherNodes) {
			if (this.eventCreator.apply(neighbor.getId())) {
				// try all neighbors until we create an event
				break;
			}
		}
	}

	public void start() {
		creatorThread.start();
	}

	/**
	 * Pauses the thread and unpauses it again. This ensures that any event that was in the process of being created is
	 * now done.
	 */
	@Override
	public void clear() {
		creatorThread.pause();
		creatorThread.resume();
	}
}

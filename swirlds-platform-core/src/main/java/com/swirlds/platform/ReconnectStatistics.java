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

import com.swirlds.common.statistics.StatEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReconnectStatistics {

	private static final String RECONNECT_CATEGORY = "Reconnect";

	private static final String FORMAT_INT = "%d";

	private final AtomicInteger senderStartTimes;

	private final AtomicInteger receiverStartTimes;

	private final AtomicInteger senderEndTimes;

	private final AtomicInteger receiverEndTimes;

	public ReconnectStatistics() {
		this.senderStartTimes = new AtomicInteger();
		this.senderEndTimes = new AtomicInteger();
		this.receiverStartTimes = new AtomicInteger();
		this.receiverEndTimes = new AtomicInteger();
	}

	void registerStats(final List<StatEntry> statEntries) {
		statEntries.add(new StatEntry(
				RECONNECT_CATEGORY,
				"startsReconnectAsReceiver",
				"number of times a node starts reconnect as a receiver",
				FORMAT_INT,
				null,
				null,
				null,
				receiverStartTimes::get)
		);

		statEntries.add(new StatEntry(
				RECONNECT_CATEGORY,
				"endsReconnectAsReceiver",
				"number of times a node ends reconnect as a receiver",
				FORMAT_INT,
				null,
				null,
				null,
				receiverEndTimes::get)
		);

		statEntries.add(new StatEntry(
				RECONNECT_CATEGORY,
				"startsReconnectAsSender",
				"number of times a node starts reconnect as a sender",
				FORMAT_INT,
				null,
				null,
				null,
				senderStartTimes::get)
		);

		statEntries.add(new StatEntry(
				RECONNECT_CATEGORY,
				"endsReconnectAsSender",
				"number of times a node ends reconnect as a sender",
				FORMAT_INT,
				null,
				null,
				null,
				senderEndTimes::get)
		);
	}

	public void incrementSenderStartTimes() {
		senderStartTimes.incrementAndGet();
	}

	public void incrementReceiverStartTimes() {
		receiverStartTimes.incrementAndGet();
	}

	public void incrementSenderEndTimes() {
		senderEndTimes.incrementAndGet();
	}

	public void incrementReceiverEndTimes() {
		receiverEndTimes.incrementAndGet();
	}
}

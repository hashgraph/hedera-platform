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

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metric;

import java.util.List;

public final class ReconnectStatistics {

	private static final String RECONNECT_CATEGORY = "Reconnect";

	private final Counter senderStartTimes = new Counter(
			RECONNECT_CATEGORY,
			"startsReconnectAsSender",
			"number of times a node starts reconnect as a sender"
	);

	private final Counter receiverStartTimes = new Counter(
			RECONNECT_CATEGORY,
			"startsReconnectAsReceiver",
			"number of times a node starts reconnect as a receiver"
	);

	private final Counter senderEndTimes = new Counter(
			RECONNECT_CATEGORY,
			"endsReconnectAsSender",
			"number of times a node ends reconnect as a sender"
	);

	private final Counter receiverEndTimes = new Counter(
			RECONNECT_CATEGORY,
			"endsReconnectAsReceiver",
			"number of times a node ends reconnect as a receiver"
	);

	void registerStats(final List<Metric> statEntries) {
		statEntries.addAll(List.of(
				receiverStartTimes,
				receiverEndTimes,
				senderStartTimes,
				senderEndTimes
		));
	}

	public void incrementSenderStartTimes() {
		senderStartTimes.increment();
	}

	public void incrementReceiverStartTimes() {
		receiverStartTimes.increment();
	}

	public void incrementSenderEndTimes() {
		senderEndTimes.increment();
	}

	public void incrementReceiverEndTimes() {
		receiverEndTimes.increment();
	}
}

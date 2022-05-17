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

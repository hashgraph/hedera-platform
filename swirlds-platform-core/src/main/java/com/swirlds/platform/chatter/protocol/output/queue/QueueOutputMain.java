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

package com.swirlds.platform.chatter.protocol.output.queue;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.chatter.protocol.output.SendCheck;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MessageOutput} that has a separate queue for each peer. When a message is supposed to be sent, it adds it to
 * each individual peer's queue
 *
 * @param <T>
 * 		the type of message
 */
public class QueueOutputMain<T extends SelfSerializable> implements MessageOutput<T> {
	private final List<QueueOutputPeer<T>> peerInstances;
	private final AverageAndMax stats;

	public QueueOutputMain(final String queueName) {
		peerInstances = new ArrayList<>();
		stats = new AverageAndMax(
				"chatter",
				queueName+"Queue",
				"size of "+queueName+" queue",
				"%,8.1f",
				AverageStat.WEIGHT_VOLATILE
		);
	}

	/**
	 * {@inheritDoc}
	 */
	public void send(final T message) {
		for (final QueueOutputPeer<T> outputPeer : peerInstances) {
			outputPeer.add(message);
			stats.update(outputPeer.getQueueSize());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public MessageProvider createPeerInstance(final SendCheck<T> sendCheck) {
		final QueueOutputPeer<T> queueOutputPeer = new QueueOutputPeer<>(sendCheck);
		peerInstances.add(queueOutputPeer);
		return queueOutputPeer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<StatEntry> getStats() {
		return stats.getAllEntries();
	}
}

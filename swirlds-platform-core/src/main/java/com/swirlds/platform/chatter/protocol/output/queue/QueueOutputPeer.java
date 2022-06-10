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
import com.swirlds.platform.chatter.protocol.ChatterConnectionState;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.output.SendCheck;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Buffers messages in a queue to be sent to one particular peer
 *
 * @param <T>
 * 		the type of message sent
 */
public class QueueOutputPeer<T extends SelfSerializable> implements MessageProvider {
	private static final int QUEUE_SIZE_DEFAULT = 100_000;
	private final ArrayBlockingQueue<T> queue = new ArrayBlockingQueue<>(QUEUE_SIZE_DEFAULT);
	private final SendCheck<T> sendCheck;
	private final AtomicReference<ChatterConnectionState> state = new AtomicReference<>(ChatterConnectionState.ACTIVE);

	public QueueOutputPeer(final SendCheck<T> sendCheck) {
		this.sendCheck = sendCheck;
	}

	/**
	 * Add a message to the queue to be sent
	 *
	 * @param message
	 * 		the message to add
	 * @throws IllegalStateException
	 * 		if the queue is full
	 */
	public void add(final T message) {
		if (state.get() != ChatterConnectionState.ACTIVE) {
			return;
		}
		if (!queue.offer(message)) {
			state.set(ChatterConnectionState.OUT_OF_SYNC);
			queue.clear();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SelfSerializable getMessage() {
		T message;
		while ((message = queue.peek()) != null) {
			switch (sendCheck.shouldSend(message)) {
				case SEND -> {
					queue.poll();
					return message;
				}
				case DISCARD -> queue.poll();
				case WAIT -> {
					return null;
				}
			}
		}
		return null;
	}

	/**
	 * @return the size of the queue held by this instance
	 */
	public int getQueueSize(){
		return queue.size();
	}
}

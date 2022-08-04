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

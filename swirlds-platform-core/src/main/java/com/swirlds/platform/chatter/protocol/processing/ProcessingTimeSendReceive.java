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

package com.swirlds.platform.chatter.protocol.processing;

import com.swirlds.common.Clock;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.chatter.protocol.MessageProvider;

import java.time.Duration;

/**
 * Sends and receives {@link ProcessingTimeMessage}s, recording the values received from peers in
 * {@link ProcessingTimes}.
 */
public class ProcessingTimeSendReceive implements MessageProvider, MessageHandler<ProcessingTimeMessage> {

	private final long peerId;
	private final Duration processingTimeInterval;
	private final ProcessingTimes processingTimes;
	private final Clock clock;

	private long lastSentTime;

	/**
	 * Creates a new instance.
	 *
	 * @param peerId
	 * 		the peer to send/receive messages to/from
	 * @param processingTimeInterval
	 * 		the interval at which to send processing time messages
	 * @param processingTimes
	 * 		the instance to store peer processing times in
	 * @param clock
	 * 		provides a point in time in nanoseconds, should only be used to measure relative time (from one point to
	 * 		another), not absolute time (wall clock time)
	 */
	public ProcessingTimeSendReceive(
			final long peerId,
			final Duration processingTimeInterval,
			final ProcessingTimes processingTimes,
			final Clock clock) {
		this.peerId = peerId;
		this.processingTimeInterval = processingTimeInterval;
		this.processingTimes = processingTimes;
		this.clock = clock;
	}

	@Override
	public void clear() {
		processingTimes.clear();
		lastSentTime = 0;
	}

	@Override
	public void handleMessage(final ProcessingTimeMessage message) {
		processingTimes.setPeerProcessingTime(peerId, message.getProcessingTimeInNanos());
	}

	@Override
	public SelfSerializable getMessage() {
		final long now = clock.now();
		if (isTimeToSendMessage(now) && hasProcessingTimeToSend()) {
			lastSentTime = now;
			return new ProcessingTimeMessage(processingTimes.getSelfProcessingTime());
		}
		return null;
	}

	private boolean hasProcessingTimeToSend() {
		return processingTimes.getSelfProcessingTime() != null;
	}

	private boolean isTimeToSendMessage(final long now) {
		return now - lastSentTime > processingTimeInterval.toNanos();
	}
}

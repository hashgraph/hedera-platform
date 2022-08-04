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

package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;

/**
 * Delays sending an event based on the difference in age between the event and maximum age provided by the {@link
 * PeerGossipState}. This was in introduced as an attempt to address the shortfalls of {@link WaitBeforeSending}.
 * Waiting based on the amount of time passed does not address any inefficiencies that might cause a delay. If the delay
 * is based on age, then the assumption is that a node notifying us of a much newer event means it is probably missing
 * this event.
 *
 * @param <E>
 * 		the type of event
 */
public class AgeBasedDelay<E extends ChatterEvent> implements SendCheck<E> {
	private final long genDiffSend;
	private final PeerGossipState state;

	/**
	 * @param genDiffSend
	 * 		the difference between the event age and max age to send an event
	 * @param state
	 * 		the gossip state of the peer
	 */
	public AgeBasedDelay(final long genDiffSend, final PeerGossipState state) {
		this.genDiffSend = genDiffSend;
		this.state = state;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SendAction shouldSend(final E event) {
		if (state.getPeerKnows(event.getDescriptor())) {
			return SendAction.DISCARD;
		}
		if (state.getMaxReceivedDescriptorGeneration() - event.getGeneration() > genDiffSend) {
			return SendAction.SEND;
		}
		return SendAction.WAIT;
	}
}

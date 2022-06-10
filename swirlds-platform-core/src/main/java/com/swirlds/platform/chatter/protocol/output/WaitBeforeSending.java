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

package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Prevents a {@link ChatterEvent} from being sent unless a specified amount of time has passed since we have received
 * this event. This excludes self events which should be sent immediately.
 *
 * @param <E>
 * 		the type of event
 */
public class WaitBeforeSending<E extends ChatterEvent> implements SendCheck<E> {
	private final int waitMillis;
	private final PeerGossipState state;
	private final Supplier<Instant> now;

	/**
	 * @param waitMillis
	 * 		wait this many milliseconds from receiving an event to sending it
	 * @param state
	 * 		the peer's state
	 * @param now
	 * 		supplies the current time
	 */
	public WaitBeforeSending(final int waitMillis, final PeerGossipState state, final Supplier<Instant> now) {
		this.waitMillis = waitMillis;
		this.state = state;
		this.now = now;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SendAction shouldSend(final E event) {
		if (state.getPeerKnows(event.getDescriptor())) {
			return SendAction.DISCARD;
		}
		if (Duration.between(event.getTimeReceived(), now.get()).toMillis() < waitMillis) {
			return SendAction.WAIT;
		}
		return SendAction.SEND;
	}
}

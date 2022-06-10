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

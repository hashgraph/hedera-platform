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

package com.swirlds.platform.network.communication.states;

import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.communication.NegotiatorBytes;

import java.io.IOException;
import java.io.InputStream;

import static com.swirlds.platform.network.communication.NegotiatorBytes.ACCEPT;
import static com.swirlds.platform.network.communication.NegotiatorBytes.KEEPALIVE;
import static com.swirlds.platform.network.communication.NegotiatorBytes.REJECT;

/**
 * We have already sent a keepalive, this state waits for, and handles, the byte sent by the peer in parallel
 */
public class SentKeepalive implements NegotiationState {
	private final InputStream byteInput;

	private final NegotiationState sleep;
	private final ReceivedInitiate receivedInitiate;

	/**
	 * @param byteInput
	 * 		the stream to read from
	 * @param sleep
	 * 		the sleep state to transition to if the peers also sends a keepalive
	 * @param receivedInitiate
	 * 		the state to transition to if the peer sends an initiate
	 */
	public SentKeepalive(final InputStream byteInput,
			final NegotiationState sleep, final ReceivedInitiate receivedInitiate) {
		this.byteInput = byteInput;
		this.sleep = sleep;
		this.receivedInitiate = receivedInitiate;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NegotiationState transition() throws NegotiationException, NetworkProtocolException, InterruptedException,
			IOException {
		final int b = byteInput.read();
		NegotiatorBytes.checkByte(b);
		return switch (b) {
			case KEEPALIVE -> sleep; // we both sent keepalive
			case ACCEPT, REJECT -> throw new NegotiationException("Unexpected ACCEPT or REJECT");
			default -> receivedInitiate.receivedInitiate(b);
		};
	}
}

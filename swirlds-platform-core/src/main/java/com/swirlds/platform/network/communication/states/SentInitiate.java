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
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.protocol.Protocol;

import java.io.IOException;
import java.io.InputStream;

/**
 * A protocol initiate was sent, this state waits for and handles the byte sent by the peer in parallel
 */
public class SentInitiate implements NegotiationState {
	private final NegotiationProtocols protocols;
	private final InputStream byteInput;

	private final ProtocolNegotiated negotiated;
	private final ReceivedInitiate receivedInitiate;
	private final WaitForAcceptReject waitForAcceptReject;
	private final NegotiationState sleep;

	private int protocolInitiated = NegotiatorBytes.UNINITIALIZED;

	/**
	 * @param protocols
	 * 		protocols being negotiated
	 * @param byteInput
	 * 		the stream to read from
	 * @param negotiated
	 * 		the state to transition to if a protocol gets negotiated
	 * @param receivedInitiate
	 * 		the state to transition to if we need to reply to the peer's initiate
	 * @param waitForAcceptReject
	 * 		the state to transition to if the peer needs to reply to our initiate
	 * @param sleep
	 * 		the sleep state to transition to if the negotiation fails
	 */
	public SentInitiate(
			final NegotiationProtocols protocols,
			final InputStream byteInput,
			final ProtocolNegotiated negotiated,
			final ReceivedInitiate receivedInitiate,
			final WaitForAcceptReject waitForAcceptReject,
			final NegotiationState sleep) {
		this.protocols = protocols;
		this.byteInput = byteInput;
		this.negotiated = negotiated;
		this.receivedInitiate = receivedInitiate;
		this.waitForAcceptReject = waitForAcceptReject;
		this.sleep = sleep;
	}

	/**
	 * Set the protocol ID that was initiated by us
	 *
	 * @param protocolId
	 * 		the ID of the protocol initiated
	 * @return this state
	 */
	public NegotiationState initiatedProtocol(final byte protocolId) {
		protocolInitiated = protocolId;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NegotiationState transition() throws NegotiationException, NetworkProtocolException, InterruptedException,
			IOException {
		final int b = byteInput.read();
		NegotiatorBytes.checkByte(b);
		final NegotiationState next = transition(b);
		protocolInitiated = NegotiatorBytes.UNINITIALIZED;
		return next;
	}

	private NegotiationState transition(final int b) throws NegotiationException {
		if (b == NegotiatorBytes.KEEPALIVE) {
			// we wait for ACCEPT or reject
			return waitForAcceptReject.initiatedProtocol(protocolInitiated);
		}
		if (b == protocolInitiated) { // both initiated the same protocol at the same time
			final Protocol protocol = protocols.getProtocol(protocolInitiated);
			if (protocol.acceptOnSimultaneousInitiate()) {
				return negotiated.runProtocol(protocols.getProtocol(b));
			} else {
				return sleep;
			}
		}
		// peer initiated a different protocol
		if (b < protocolInitiated) { // lower index means higher priority
			// THEIR protocol is higher priority, so we should ACCEPT or REJECT
			return receivedInitiate.receivedInitiate(b);
		} else {
			// OUR protocol is higher priority, so they should ACCEPT or REJECT
			return waitForAcceptReject.initiatedProtocol(protocolInitiated);
		}
	}
}

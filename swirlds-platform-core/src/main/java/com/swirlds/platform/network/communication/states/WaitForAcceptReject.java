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
import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorBytes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Waits for, and handles, an ACCEPT or REJECT to a protocol initiated by us
 */
public class WaitForAcceptReject implements NegotiationState {
	private final NegotiationProtocols protocols;
	private final InputStream byteInput;

	private final ProtocolNegotiated negotiated;
	private final NegotiationState sleep;

	private int protocolInitiated = NegotiatorBytes.UNINITIALIZED;

	/**
	 * @param protocols
	 * 		protocols being negotiated
	 * @param byteInput
	 * 		the stream to read from
	 * @param negotiated
	 * 		the state to transition to if a protocol gets accepted
	 * @param sleep
	 * 		the sleep state to transition to if the protocol gets rejected
	 */
	public WaitForAcceptReject(
			final NegotiationProtocols protocols,
			final InputStream byteInput,
			final ProtocolNegotiated negotiated,
			final NegotiationState sleep) {
		this.protocols = protocols;
		this.byteInput = byteInput;
		this.negotiated = negotiated;
		this.sleep = sleep;
	}

	/**
	 * Set the protocol ID that was initiated by us
	 *
	 * @param protocolId
	 * 		the ID of the protocol initiated
	 * @return this state
	 */
	public NegotiationState initiatedProtocol(final int protocolId) {
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
		return switch (b) {
			case NegotiatorBytes.ACCEPT -> negotiated.runProtocol(protocols.getProtocol(protocolInitiated));
			case NegotiatorBytes.REJECT -> sleep;
			default -> throw new NegotiationException(String.format(
					"Unexpected byte %d, expected ACCEPT or REJECT", b
			));
		};
	}
}

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
import java.io.OutputStream;

/**
 * Responds to a protocol initiation by the peer
 */
public class ReceivedInitiate implements NegotiationState {
	private final NegotiationProtocols protocols;
	private final OutputStream byteOutput;

	private final ProtocolNegotiated stateNegotiated;
	private final NegotiationState sleep;

	private int protocolInitiated = NegotiatorBytes.UNINITIALIZED;

	/**
	 * @param protocols
	 * 		protocols being negotiated
	 * @param byteOutput
	 * 		the stream to write to
	 * @param stateNegotiated
	 * 		the state to transition to if a protocol gets negotiated
	 * @param sleep
	 * 		the sleep state to transition to if the negotiation fails
	 */
	public ReceivedInitiate(final NegotiationProtocols protocols, final OutputStream byteOutput,
			final ProtocolNegotiated stateNegotiated, final NegotiationState sleep) {
		this.protocols = protocols;
		this.byteOutput = byteOutput;
		this.stateNegotiated = stateNegotiated;
		this.sleep = sleep;
	}

	/**
	 * Set the protocol ID that was initiated by the peer
	 *
	 * @param protocolId
	 * 		the ID of the protocol initiated
	 * @return this state
	 */
	public NegotiationState receivedInitiate(final int protocolId) {
		protocolInitiated = protocolId;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NegotiationState transition() throws NegotiationException, NetworkProtocolException, InterruptedException,
			IOException {
		final Protocol protocol = protocols.getProtocol(protocolInitiated);
		if (protocol.shouldAccept()) {
			byteOutput.write(NegotiatorBytes.ACCEPT);
			byteOutput.flush();
			stateNegotiated.runProtocol(protocol);
			protocolInitiated = NegotiatorBytes.UNINITIALIZED;
			return stateNegotiated;
		} else {
			byteOutput.write(NegotiatorBytes.REJECT);
			byteOutput.flush();
			protocolInitiated = NegotiatorBytes.UNINITIALIZED;
			return sleep;
		}
	}
}

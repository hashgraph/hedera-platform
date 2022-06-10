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

import com.swirlds.platform.network.communication.NegotiationProtocols;
import com.swirlds.platform.network.communication.NegotiatorBytes;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Sends a KEEPALIVE or a protocol ID initiating that protocol
 */
public class InitialState implements NegotiationState {
	private final NegotiationProtocols protocols;
	private final OutputStream byteOutput;

	private final SentKeepalive stateSentKeepalive;
	private final SentInitiate stateSentInitiate;

	/**
	 * @param protocols
	 * 		protocols being negotiated
	 * @param byteOutput
	 * 		the stream to write to
	 * @param stateSentKeepalive
	 * 		the state to transition to if we send a keepalive
	 * @param stateSentInitiate
	 * 		the state to transition to if we initiate a protocol
	 */
	public InitialState(
			final NegotiationProtocols protocols,
			final OutputStream byteOutput,
			final SentKeepalive stateSentKeepalive,
			final SentInitiate stateSentInitiate) {
		this.protocols = protocols;
		this.byteOutput = byteOutput;
		this.stateSentKeepalive = stateSentKeepalive;
		this.stateSentInitiate = stateSentInitiate;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NegotiationState transition() throws IOException {
		final byte protocolByte = protocols.getProtocolToInitiate();

		if (protocolByte >= 0) {
			byteOutput.write(protocolByte);
			byteOutput.flush();
			return stateSentInitiate.initiatedProtocol(protocolByte);
		} else {
			byteOutput.write(NegotiatorBytes.KEEPALIVE);
			byteOutput.flush();
			return stateSentKeepalive;
		}
	}
}

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

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;
import com.swirlds.platform.network.protocol.Protocol;

import java.io.IOException;

/**
 * Runs a protocol previously negotiated
 */
public class ProtocolNegotiated implements NegotiationState {
	private final SyncConnection connection;
	private Protocol protocol;

	/**
	 * @param connection
	 * 		the connection to run the protocol on
	 */
	public ProtocolNegotiated(final SyncConnection connection) {
		this.connection = connection;
	}

	/**
	 * Set the protocol to run on the next transition
	 *
	 * @param protocol
	 * 		the protocol to run
	 * @return this state
	 */
	public NegotiationState runProtocol(final Protocol protocol) {
		this.protocol = protocol;
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NegotiationState transition() throws NegotiationException, NetworkProtocolException, IOException,
			InterruptedException {
		if (protocol == null) {
			throw new IllegalStateException("Cannot run a protocol because it is null");
		}
		try {
			protocol.runProtocol(connection);
		} finally {
			protocol = null;
		}
		return null; //back to initial state
	}
}

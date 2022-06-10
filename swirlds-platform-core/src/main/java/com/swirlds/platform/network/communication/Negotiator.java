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

package com.swirlds.platform.network.communication;

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.states.InitialState;
import com.swirlds.platform.network.communication.states.ProtocolNegotiated;
import com.swirlds.platform.network.communication.states.ReceivedInitiate;
import com.swirlds.platform.network.communication.states.SentInitiate;
import com.swirlds.platform.network.communication.states.SentKeepalive;
import com.swirlds.platform.network.communication.states.Sleep;
import com.swirlds.platform.network.communication.states.NegotiationState;
import com.swirlds.platform.network.communication.states.WaitForAcceptReject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A state machine responsible for negotiating the protocol to run over the provided connection
 */
public class Negotiator {
	private final NegotiationState initialState;
	private final ProtocolNegotiated protocolNegotiated;
	private final Sleep sleep;
	private boolean errorState;

	/**
	 * @param protocols
	 * 		all possible protocols that could run over this connection
	 * @param connection
	 * 		the connection to negotiate and run the protocol on
	 * @param sleepMs
	 * 		the number of milliseconds to sleep if a negotiation fails
	 */
	public Negotiator(final NegotiationProtocols protocols, final SyncConnection connection, final int sleepMs) {
		protocolNegotiated = new ProtocolNegotiated(connection);
		sleep = new Sleep(sleepMs);
		final InputStream in = connection.getDis();
		final OutputStream out = connection.getDos();
		final ReceivedInitiate receivedInitiate = new ReceivedInitiate(protocols, out, protocolNegotiated, sleep);
		final WaitForAcceptReject waitForAcceptReject = new WaitForAcceptReject(
				protocols, in, protocolNegotiated, sleep);
		final SentInitiate sentInitiate = new SentInitiate(
				protocols, in, protocolNegotiated, receivedInitiate, waitForAcceptReject, sleep);
		final SentKeepalive sentKeepalive = new SentKeepalive(in, sleep, receivedInitiate);
		this.initialState = new InitialState(protocols, out, sentKeepalive, sentInitiate);
		this.errorState = false;
	}

	/**
	 * Execute a single cycle of protocol negotiation
	 *
	 * @throws NegotiationException
	 * 		if an issue occurs during protocol negotiation
	 * @throws NetworkProtocolException
	 * 		if a protocol specific issue occurs
	 * @throws IOException
	 * 		if an I/O issue occurs
	 * @throws InterruptedException
	 * 		if the calling thread is interrupted while running the protocol
	 */
	public void execute() throws InterruptedException, NegotiationException, NetworkProtocolException, IOException {
		if (errorState) {
			throw new IllegalStateException();
		}
		NegotiationState prev = null;
		NegotiationState current = initialState;
		while (current != null) {
			try {
				prev = current;
				current = current.transition();
			} catch (final RuntimeException
					| NegotiationException
					| NetworkProtocolException
					| InterruptedException
					| IOException e) {
				errorState = true;
				throw e;
			}
		}
		if (prev != sleep && prev != protocolNegotiated) {
			throw new NegotiationException("The outcome should always be sleep or running a protocol");
		}
	}
}

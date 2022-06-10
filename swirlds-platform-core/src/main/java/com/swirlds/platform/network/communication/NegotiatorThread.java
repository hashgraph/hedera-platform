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

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;

import java.io.IOException;

/**
 * Continuously runs protocol negotiation and protocols over connections supplied by the connection manager
 */
public class NegotiatorThread implements InterruptableRunnable {
	private static final int SLEEP_MS = 100;
	private final ConnectionManager connectionManager;
	private final NegotiationProtocols protocols;

	/**
	 * @param connectionManager
	 * 		supplies network connections
	 * @param protocols
	 * 		the protocols to negotiate and run
	 */
	public NegotiatorThread(
			final ConnectionManager connectionManager,
			final NegotiationProtocols protocols) {
		this.connectionManager = connectionManager;
		this.protocols = protocols;
	}

	@Override
	public void run() throws InterruptedException {
		final SyncConnection currentConn = connectionManager.waitForConnection();
		final Negotiator negotiator = new Negotiator(
				protocols,
				currentConn,
				SLEEP_MS
		);
		while (currentConn.connected()) {
			try {
				negotiator.execute();
			} catch (final RuntimeException | IOException | NetworkProtocolException | NegotiationException e) {
				NetworkUtils.handleNetworkException(e, currentConn);
			}
		}
	}
}

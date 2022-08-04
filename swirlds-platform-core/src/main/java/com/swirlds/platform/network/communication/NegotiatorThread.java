/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

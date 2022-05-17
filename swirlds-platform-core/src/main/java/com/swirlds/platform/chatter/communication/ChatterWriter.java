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

package com.swirlds.platform.chatter.communication;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.threading.InterruptableRunnable;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkUtils;

import java.io.IOException;

/**
 * Polls chatter for messages and serializes them to the stream. This is a temporary
 * class for the POC, it will be replaced in the near future.
 */
public class ChatterWriter implements InterruptableRunnable {
	private final ConnectionManager connectionManager;
	private final MessageProvider messageProvider;

	public ChatterWriter(final ConnectionManager connectionManager, final MessageProvider messageProvider) {
		this.connectionManager = connectionManager;
		this.messageProvider = messageProvider;
	}

	@Override
	public void run() throws InterruptedException {
		final SyncConnection currentConn = connectionManager.waitForConnection();
		while (currentConn.connected()) {
			try {
				final SelfSerializable message = messageProvider.getMessage();
				if (message == null) {
					currentConn.getDos().flush();// only flush before a sleep
					Thread.sleep(Constants.NO_PAYLOAD_SLEEP_MS);
					currentConn.getDos().writeByte(Constants.KEEPALIVE);
					return;
				}
				currentConn.getDos().writeByte(Constants.PAYLOAD);
				currentConn.getDos().writeSerializable(message, true);
			} catch (final RuntimeException | IOException e) {
				NetworkUtils.handleNetworkException(e, currentConn);
			}
		}
	}
}

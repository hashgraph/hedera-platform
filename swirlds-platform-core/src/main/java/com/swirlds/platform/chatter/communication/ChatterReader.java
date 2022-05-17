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
import com.swirlds.platform.chatter.protocol.PeerMessageException;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkUtils;

import java.io.IOException;

/**
 * Reads {@link SelfSerializable} messages from a stream a passes the on to chatter for handling. This is a temporary
 * class for the POC, it will be replaced in the near future.
 */
public class ChatterReader implements InterruptableRunnable {
	private final ConnectionManager connectionManager;
	private final PeerMessageHandler messageHandler;

	public ChatterReader(final ConnectionManager connectionManager,
			final PeerMessageHandler messageHandler) {
		this.connectionManager = connectionManager;
		this.messageHandler = messageHandler;
	}

	@Override
	public void run() throws InterruptedException {
		final SyncConnection currentConn = connectionManager.waitForConnection();
		while (currentConn.connected()) {
			try {
				final byte b = currentConn.getDis().readByte();
				if (b == Constants.PAYLOAD) {
					final SelfSerializable message = currentConn.getDis().readSerializable();
					messageHandler.handleMessage(
							message
					);
				}
			} catch (final RuntimeException | IOException | PeerMessageException e) {
				NetworkUtils.handleNetworkException(e, currentConn);
			}
		}
	}
}

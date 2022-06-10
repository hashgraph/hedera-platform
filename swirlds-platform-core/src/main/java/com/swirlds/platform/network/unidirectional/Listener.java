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
package com.swirlds.platform.network.unidirectional;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.NetworkUtils;

import java.io.IOException;
import java.util.Objects;

/**
 * Listen for incoming network protocol requests then calls the handler. If it does not have a connection, or the
 * connection is closed, it will block waiting for a new connection.
 */
public class Listener implements InterruptableRunnable {
	/** handles incoming network protocol requests */
	private final NetworkProtocolResponder protocolHandler;
	/** a blocking point to wait for a connection if the current one is invalid */
	private final ConnectionManager connectionManager;

	public Listener(final NetworkProtocolResponder protocolHandler, final ConnectionManager connectionManager) {
		Objects.requireNonNull(protocolHandler);
		Objects.requireNonNull(connectionManager);
		this.protocolHandler = protocolHandler;
		this.connectionManager = connectionManager;
	}

	@Override
	public void run() throws InterruptedException {
		final SyncConnection currentConn = connectionManager.waitForConnection();
		try {
			// wait for a request to be received, and pass it on to the handler
			final byte b = currentConn.getDis().readByte();
			protocolHandler.protocolInitiated(b, currentConn);
		} catch (final RuntimeException | IOException | NetworkProtocolException e) {
			NetworkUtils.handleNetworkException(e, currentConn);
		}
	}
}

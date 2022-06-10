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
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;

import java.io.IOException;

/**
 * An instance responsible for serializing and deserializing chatter messages for a single peer
 */
public class ChatterProtocol implements Protocol {
	private final PeerMessageHandler messageHandler;
	private final MessageProvider messageProvider;
	private final ParallelExecutor parallelExecutor;
	// ATM if chatter ends or is interrupted, it can not run again
	private boolean ranOnce;

	public ChatterProtocol(
			final PeerInstance peerInstance,
			final ParallelExecutor parallelExecutor) {
		this.messageHandler = peerInstance.inputHandler();
		this.messageProvider = peerInstance.outputAggregator();
		this.parallelExecutor = parallelExecutor;
		this.ranOnce = false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void runProtocol(final SyncConnection connection)
			throws NetworkProtocolException, IOException, InterruptedException {
		ranOnce = true;
		try {
			parallelExecutor.doParallel(
					() -> read(connection),
					() -> write(connection),
					connection::disconnect
			);
		} catch (final ParallelExecutionException e) {
			if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
				throw new IOException(e);
			}
			throw new NetworkProtocolException(e);
		}
	}

	/**
	 * Reads {@link SelfSerializable} messages from a stream and passes them on to chatter for handling
	 */
	private void read(final SyncConnection connection) throws NetworkProtocolException, IOException {
		while (connection.connected()) {
			final byte b = connection.getDis().readByte();
			if (b == Constants.PAYLOAD) {
				final SelfSerializable message = connection.getDis().readSerializable();
				messageHandler.handleMessage(message);
			}
		}
	}

	/**
	 * Polls chatter for messages and serializes them to the stream
	 */
	public void write(final SyncConnection connection) throws InterruptedException, IOException {
		while (connection.connected()) {
			final SelfSerializable message = messageProvider.getMessage();
			if (message == null) {
				connection.getDos().flush();// only flush before a sleep
				Thread.sleep(Constants.NO_PAYLOAD_SLEEP_MS);
				connection.getDos().writeByte(Constants.KEEPALIVE);
				continue;
			}
			connection.getDos().writeByte(Constants.PAYLOAD);
			connection.getDos().writeSerializable(message, true);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldInitiate() {
		return !ranOnce;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean shouldAccept() {
		return !ranOnce;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean acceptOnSimultaneousInitiate() {
		return true;
	}
}

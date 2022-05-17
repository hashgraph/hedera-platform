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

package com.swirlds.platform.reconnect;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.platform.ReconnectStatistics;
import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.unidirectional.NetworkProtocolResponder;
import com.swirlds.platform.state.SignedState;
import com.swirlds.platform.state.SignedStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static com.swirlds.logging.LogMarker.RECONNECT;

public class ReconnectProtocolResponder implements NetworkProtocolResponder {
	private static final Logger LOG = LogManager.getLogger();

	private final SignedStateManager signedStateManager;
	private final ReconnectSettings settings;
	/**
	 * This object is responsible for limiting the frequency of reconnect attempts (in the role of the sender)
	 */
	private final ReconnectThrottle reconnectThrottle;
	private final ReconnectStatistics stats;

	public ReconnectProtocolResponder(
			final SignedStateManager signedStateManager,
			final ReconnectSettings settings,
			final ReconnectThrottle reconnectThrottle,
			final ReconnectStatistics stats) {
		this.signedStateManager = signedStateManager;
		this.settings = settings;
		this.reconnectThrottle = reconnectThrottle;
		this.stats = stats;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void protocolInitiated(final byte initialByte, final SyncConnection connection)
			throws IOException, NetworkProtocolException {
		LOG.info(RECONNECT.getMarker(), "{} got COMM_STATE_REQUEST from {}",
				connection.getSelfId(), connection.getOtherId());

		// the SignedState is later manually released by the ReconnectTeacher
		final SignedState state = signedStateManager.getLastCompleteSignedState().get();

		new ReconnectTeacher(
				connection,
				state,
				settings.getAsyncStreamTimeoutMilliseconds(),
				reconnectThrottle,
				connection.getSelfId().getId(),
				connection.getOtherId().getId(),
				state.getLastRoundReceived(),
				stats).execute();
	}
}

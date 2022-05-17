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

import com.swirlds.platform.SyncConnection;
import com.swirlds.platform.network.ByteConstants;

import java.io.IOException;

/**
 * Handles incoming heartbeat protocol requests
 */
public final class HeartbeatProtocolResponder {

	private HeartbeatProtocolResponder() {
	}

	/**
	 * A static heartbeat implementation of {@link NetworkProtocolResponder} since the protocol is stateless
	 */
	public static void heartbeatProtocol(final byte ignored, final SyncConnection connection)
			throws IOException {
		connection.getDos().writeByte(ByteConstants.HEARTBEAT_ACK);
		connection.getDos().flush();
	}
}

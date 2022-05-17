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
import com.swirlds.platform.network.NetworkProtocolException;

import java.io.IOException;
import java.util.List;

/**
 * Accepts incoming protocol requests and passes them on to the appropriate handler based on the value of the initial
 * byte
 */
public class MultiProtocolResponder implements NetworkProtocolResponder {
	private final List<ProtocolMapping> mappings;

	public MultiProtocolResponder(final List<ProtocolMapping> mappings) {
		this.mappings = mappings;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void protocolInitiated(final byte initialByte, final SyncConnection connection)
			throws IOException, NetworkProtocolException, InterruptedException {
		for (final ProtocolMapping mapping : mappings) {
			if (mapping.initialByte() == initialByte) {
				mapping.protocolHandler().protocolInitiated(initialByte, connection);
				return;
			}
		}
		throw new NetworkProtocolException(String.format("No protocol registered for byte: %02x", initialByte));
	}

}

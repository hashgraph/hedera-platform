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

package com.swirlds.platform.network.unidirectional;

import com.swirlds.platform.Connection;
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
	public void protocolInitiated(final byte initialByte, final Connection connection)
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

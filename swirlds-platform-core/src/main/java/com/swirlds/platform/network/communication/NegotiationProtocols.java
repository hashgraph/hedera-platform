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

import com.swirlds.platform.network.protocol.Protocol;

import java.util.List;

/**
 * Manages protocols during a protocol negotiation
 */
public class NegotiationProtocols {
	private final Protocol[] allProtocols;

	/**
	 * @param protocols
	 * 		a list of protocols to negotiate in order of priority
	 */
	public NegotiationProtocols(final List<Protocol> protocols) {
		if (protocols == null || protocols.isEmpty() || protocols.size() > NegotiatorBytes.MAX_NUMBER_OF_PROTOCOLS) {
			throw new IllegalArgumentException(
					"the list of protocols supplied should be: non-null, non-empty and have a size lower than "
					+ NegotiatorBytes.MAX_NUMBER_OF_PROTOCOLS
			);
		}
		this.allProtocols = protocols.toArray(new Protocol[0]);
	}

	/**
	 * Get the protocol with the supplied ID
	 *
	 * @param id
	 * 		the ID of the protocol
	 * @return the protocol requested
	 * @throws NegotiationException
	 * 		if an invalid ID is supplied
	 */
	public Protocol getProtocol(final int id) throws NegotiationException {
		if (id < 0 || id >= allProtocols.length) {
			throw new NegotiationException("not a valid protocol ID: " + id);
		}
		return allProtocols[id];
	}

	/**
	 * @return the ID of the protocol that should be initiated, or -1 if none
	 */
	public byte getProtocolToInitiate() {
		// check each protocol in order of priority until we find one we should initiate
		for (byte i = 0; i < allProtocols.length; i++) {
			if (allProtocols[i].shouldInitiate()) {
				return i;
			}
		}
		return -1;
	}

}

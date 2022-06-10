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

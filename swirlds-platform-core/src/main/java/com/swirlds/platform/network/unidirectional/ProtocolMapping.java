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

/**
 * Mapping between a protocol initiation byte and a handler for this protocol
 *
 * @param initialByte
 * 		the byte that start the protocol
 * @param protocolHandler
 * 		the handler responsible for this protocol
 */
public record ProtocolMapping(byte initialByte, NetworkProtocolResponder protocolHandler) {
	/**
	 * Creates a mapping between the supplied byte and handler
	 *
	 * @param initialByte
	 * 		the byte to map
	 * @param protocolHandler
	 * 		the handler to map
	 * @return the new mapping
	 */
	public static ProtocolMapping map(final byte initialByte, final NetworkProtocolResponder protocolHandler) {
		return new ProtocolMapping(initialByte, protocolHandler);
	}
}

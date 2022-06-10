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

package com.swirlds.platform.network.communication.states;

import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.communication.NegotiationException;

import java.io.IOException;

/**
 * Represents a single state in a negotiation state machine
 */
@FunctionalInterface
public interface NegotiationState {
	/**
	 * Transitions to the next negotiation state
	 *
	 * @return the next state, or null if the negotiation ended
	 * @throws NegotiationException
	 * 		if an issue occurs during negotiation
	 * @throws NetworkProtocolException
	 * 		if a protocol is negotiated and issue occurs while running it
	 * @throws InterruptedException
	 * 		if the thread running this is interrupted
	 * @throws IOException
	 * 		if an IO error occurs with the connection used
	 */
	NegotiationState transition() throws NegotiationException, NetworkProtocolException, InterruptedException,
			IOException;
}

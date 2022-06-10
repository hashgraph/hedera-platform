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

package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.stats.StatsProvider;

/**
 * Manages messages that need to be sent to multiple peers
 *
 * @param <T>
 * 		the type of message managed
 */
public interface MessageOutput<T extends SelfSerializable> extends StatsProvider {
	/**
	 * Creates an instance responsible for sending messages to one particular peer
	 *
	 * @param sendCheck
	 * 		invoked before a message is about to be sent, to determine if it should be sent or not
	 * @return a message provider for a peer
	 */
	MessageProvider createPeerInstance(final SendCheck<T> sendCheck);

	/**
	 * Send a message to all peers
	 *
	 * @param message
	 * 		the message to send
	 */
	void send(T message);
}

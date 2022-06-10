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

package com.swirlds.platform.chatter.protocol.input;

import com.swirlds.common.constructable.ClassIdFormatter;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.PeerMessageException;
import com.swirlds.platform.chatter.protocol.PeerMessageHandler;
import com.swirlds.platform.stats.PerSecondStat;

import java.util.List;

/**
 * Determines the type of message received from a peer and passes it on to an appropriate handler
 */
public class InputDelegate implements PeerMessageHandler {
	private final List<MessageTypeHandler<? extends SelfSerializable>> handlers;
	private final PerSecondStat msgPerSecond;

	public InputDelegate(
			final List<MessageTypeHandler<? extends SelfSerializable>> handlers,
			final PerSecondStat msgPerSecond) {
		this.handlers = handlers;
		this.msgPerSecond = msgPerSecond;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleMessage(final SelfSerializable message) throws PeerMessageException {
		msgPerSecond.increment();
		for (final MessageTypeHandler<?> caster : handlers) {
			if (caster.castHandleMessage(message)) {
				return;
			}
		}
		throw new PeerMessageException("Unrecognized message type: " + ClassIdFormatter.classIdString(message));
	}
}

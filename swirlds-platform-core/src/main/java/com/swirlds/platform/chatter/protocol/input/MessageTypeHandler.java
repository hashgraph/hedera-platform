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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.platform.chatter.protocol.MessageHandler;

import java.util.List;

/**
 * Handles messages of a single type
 *
 * @param <T>
 * 		the type of message
 */
public class MessageTypeHandler<T extends SelfSerializable> {
	private final List<MessageHandler<T>> handlers;
	private final Class<T> messageType;

	public MessageTypeHandler(final List<MessageHandler<T>> handlers, final Class<T> messageType) {
		this.handlers = handlers;
		this.messageType = messageType;
	}

	/**
	 * If the message is the appropriate type, cast it and pass it on to handlers for the message type
	 *
	 * @param message
	 * 		the message to cast and handle
	 * @return true if the message is the appropriate type and is handled by this instance, false otherwise
	 */
	public boolean castHandleMessage(final SelfSerializable message) {
		if (messageType.isInstance(message)) {
			final T cast = messageType.cast(message);
			for (final MessageHandler<T> handler : handlers) {
				handler.handleMessage(cast);
			}
			return true;
		}
		return false;
	}
}

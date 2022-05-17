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

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for {@link MessageHandler}
 *
 * @param <T>
 * 		the type of message
 */
public final class MessageTypeHandlerBuilder<T extends SelfSerializable> {
	private final Class<T> messageType;
	private final List<MessageHandler<T>> handlers;

	private MessageTypeHandlerBuilder(final Class<T> messageType) {
		this.messageType = messageType;
		this.handlers = new ArrayList<>();
	}

	/**
	 * Create a new builder
	 *
	 * @param messageType
	 * 		the class of the message to handle
	 * @param <M>
	 * 		the type defined by messageType
	 * @return a new builder
	 */
	public static <M extends SelfSerializable> MessageTypeHandlerBuilder<M> builder(final Class<M> messageType) {
		return new MessageTypeHandlerBuilder<>(messageType);
	}

	/**
	 * Add a handler for the specified message type
	 *
	 * @param handler
	 * 		the handler to add
	 * @return this instance
	 */
	public MessageTypeHandlerBuilder<T> addHandler(final MessageHandler<T> handler) {
		this.handlers.add(handler);
		return this;
	}

	/**
	 * Same as {@link #addHandler(MessageHandler)} but for a list
	 */
	public MessageTypeHandlerBuilder<T> addHandlers(final List<MessageHandler<T>> handlers) {
		this.handlers.addAll(handlers);
		return this;
	}

	/**
	 * @return a new handler
	 */
	public MessageTypeHandler<T> build() {
		return new MessageTypeHandler<>(handlers, messageType);
	}
}

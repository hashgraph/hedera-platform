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
import com.swirlds.platform.stats.PerSecondStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A builder for {@link InputDelegate}
 */
public final class InputDelegateBuilder {
	private final List<MessageTypeHandler<? extends SelfSerializable>> messageTypeHandlers = new ArrayList<>();
	private PerSecondStat stat;

	private InputDelegateBuilder() {
	}

	/**
	 * @return a new builder
	 */
	public static InputDelegateBuilder builder() {
		return new InputDelegateBuilder();
	}

	/**
	 * Add a handler for a particular message type
	 *
	 * @param handler
	 * 		the handler to add
	 * @return this instance
	 */
	public InputDelegateBuilder addHandler(final MessageTypeHandler<? extends SelfSerializable> handler) {
		messageTypeHandlers.add(handler);
		return this;
	}

	/**
	 * Use this statistic to track the number messages received per second
	 *
	 * @param stat
	 * 		the instance that will track
	 * @return this instance
	 */
	public InputDelegateBuilder setStat(final PerSecondStat stat) {
		this.stat = stat;
		return this;
	}

	/**
	 * @return a new {@link InputDelegate}
	 */
	public InputDelegate build() {
		if (messageTypeHandlers.isEmpty()) {
			throw new IllegalStateException("Add least 1 handler should be added");
		}
		return new InputDelegate(messageTypeHandlers, Objects.requireNonNull(stat));
	}
}

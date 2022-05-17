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

import java.util.List;

/**
 * Holds a list of message providers in order of priority. When a message is requested, it will check the providers in
 * priority order until it finds one that has messages to be sent.
 */
public class PriorityOutputAggregator implements MessageProvider {
	private final List<MessageProvider> providers;

	public PriorityOutputAggregator(final List<MessageProvider> providers) {
		this.providers = providers;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SelfSerializable getMessage() {
		for (final MessageProvider provider : providers) {
			final SelfSerializable message = provider.getMessage();
			if (message != null) {
				return message;
			}
		}
		return null;
	}
}

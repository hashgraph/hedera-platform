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

package com.swirlds.platform.chatter;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.chatter.protocol.MessageHandler;
import com.swirlds.platform.event.GossipEvent;

/**
 * Prepares a {@link GossipEvent} received from a peer for further handling
 */
public class PrepareChatterEvent implements MessageHandler<GossipEvent> {
	private final Cryptography cryptography;

	public PrepareChatterEvent(final Cryptography cryptography) {
		this.cryptography = cryptography;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void handleMessage(final GossipEvent event) {
		final BaseEventHashedData hashedData = event.getHashedData();
		cryptography.digestSync(hashedData);
		event.buildDescriptor();
	}
}

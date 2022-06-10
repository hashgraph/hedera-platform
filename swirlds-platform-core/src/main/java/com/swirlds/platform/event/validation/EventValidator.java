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

package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * Validates events received from peers
 */
public class EventValidator {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger LOG = LogManager.getLogger();

	private final GossipEventValidator gossipEventValidator;
	/** A consumer of valid events */
	private final Consumer<GossipEvent> eventIntake;
	private final Cryptography cryptography;

	public EventValidator(
			final GossipEventValidator gossipEventValidator,
			final Consumer<GossipEvent> eventIntake) {
		this.gossipEventValidator = gossipEventValidator;
		this.eventIntake = eventIntake;
		this.cryptography = CryptoFactory.getInstance();
	}

	/**
	 * Hashes the event if it hasn't been hashed already, then checks the event's validity. If the event is invalid, it
	 * is discarded. If it's valid, it is passed on.
	 *
	 * @param gossipEvent
	 * 		event received from gossip
	 */
	public void validateEvent(final GossipEvent gossipEvent) {
		try {
			if (gossipEvent.getHashedData().getHash() == null) {
				// only hash if it hasn't been already hashed
				cryptography.digestSync(gossipEvent.getHashedData());
				// we also need to build the descriptor once we have the hash
				gossipEvent.buildDescriptor();
			}
			if (!gossipEventValidator.isEventValid(gossipEvent)) {
				return;
			}
			eventIntake.accept(gossipEvent);
		} catch (final RuntimeException e) {
			LOG.error(EXCEPTION.getMarker(), "Error while processing intake event", e);
		}
	}

}

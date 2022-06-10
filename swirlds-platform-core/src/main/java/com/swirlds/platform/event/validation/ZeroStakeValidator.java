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

import com.swirlds.common.system.AddressBook;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * A {@link GossipEventValidator} which checks if an event has been created by a zero stake node
 */
public class ZeroStakeValidator implements GossipEventValidator {
	private static final Logger LOG = LogManager.getLogger();
	private final AddressBook addressBook;

	public ZeroStakeValidator(final AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEventValid(final GossipEvent event) {
		// If beta mirror node logic is enabled and the event originated from a node known to have a zero stake,
		// then we should discard this event and not even worry about validating the signature in order
		// to prevent a potential DoS or DDoS attack from a zero stake node
		if (addressBook.isZeroStakeNode(event.getHashedData().getCreatorId())) {
			LOG.debug(INVALID_EVENT_ERROR.getMarker(),
					"Event Intake: Received event data from a zero stake node {}}",
					() -> EventStrings.toShortString(event));
			return false;
		}
		return true;
	}
}

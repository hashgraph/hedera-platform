/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.event.validation;

import com.swirlds.common.system.address.AddressBook;
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
		if (addressBook.getAddress(event.getHashedData().getCreatorId()).isZeroStake()) {
			LOG.debug(INVALID_EVENT_ERROR.getMarker(),
					"Event Intake: Received event data from a zero stake node {}}",
					() -> EventStrings.toShortString(event));
			return false;
		}
		return true;
	}
}

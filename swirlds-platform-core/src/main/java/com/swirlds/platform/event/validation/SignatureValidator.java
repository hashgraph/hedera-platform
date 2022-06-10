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

import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PublicKey;

import static com.swirlds.logging.LogMarker.EVENT_SIG;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * A {@link GossipEventValidator} which validates the event's signature
 */
public class SignatureValidator implements GossipEventValidator {
	private static final Logger LOG = LogManager.getLogger();
	private final AddressBook addressBook;

	public SignatureValidator(final AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	/**
	 * Validates an event's signature
	 *
	 * @param event
	 * 		the event to be verified
	 * @param publicKey
	 * 		the public used to validate the event signature
	 * @return true iff the signature is crypto-verified to be correct
	 */
	private static boolean isValidSignature(final BaseEvent event, final PublicKey publicKey) {
		LOG.debug(EVENT_SIG.getMarker(),
				"event signature is about to be verified. {}",
				() -> EventStrings.toShortString(event));

		final boolean valid = CryptoStatic.verifySignature(
				event.getHashedData().getHash().getValue(),
				event.getUnhashedData().getSignature(),
				publicKey);

		if (!valid) {
			LOG.error(INVALID_EVENT_ERROR.getMarker(),
					"""
							failed the signature check {}
							with sig {}
							and hash {}
							transactions are null: {} (if true, this is probably an event from a signed state)""",
					() -> event,
					() -> CommonUtils.hex(event.getUnhashedData().getSignature()),
					() -> event.getHashedData().getHash(),
					() -> event.getHashedData().getTransactions() == null);
		}

		return valid;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEventValid(final GossipEvent event) {
		final long creatorId = event.getHashedData().getCreatorId();
		final Address address = addressBook.getAddress(creatorId);
		if (address == null) {
			LOG.error(EXCEPTION.getMarker(), "Cannot find address for creator with ID: {}", () -> creatorId);
			return false;
		}
		final PublicKey publicKey = address.getSigPublicKey();
		return isValidSignature(event, publicKey);
	}
}

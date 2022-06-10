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

import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;

/**
 * Determines whether total size of all transactions in a given event is too large
 */
public class TransactionSizeValidator implements GossipEventValidator {
	private static final Logger LOG = LogManager.getLogger();
	private final int maxTransactionBytesPerEvent;

	public TransactionSizeValidator(final int maxTransactionBytesPerEvent) {
		this.maxTransactionBytesPerEvent = maxTransactionBytesPerEvent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEventValid(final GossipEvent event) {
		if (event.getHashedData().getTransactions() == null) {
			return true;
		}

		// Sum the total size of all transactions about to be included in this event
		int tmpEventTransSize = 0;
		for (final Transaction t : event.getHashedData().getTransactions()) {
			tmpEventTransSize += t.getSerializedLength();
		}
		final int finalEventTransSize = tmpEventTransSize;

		// Ignore & log if we have encountered a transaction larger than the limit
		// This might be due to a malicious node in the network
		if (tmpEventTransSize > maxTransactionBytesPerEvent) {
			LOG.error(INVALID_EVENT_ERROR.getMarker(),
					"maxTransactionBytesPerEvent exceeded by event {} with a total size of {} bytes",
					() -> EventStrings.toShortString(event),
					() -> finalEventTransSize);
			return false;
		}

		return true;
	}
}

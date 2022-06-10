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

package com.swirlds.common.system.transaction;

import com.swirlds.common.io.SerializableWithKnownLength;

import static com.swirlds.common.system.transaction.TransactionType.APPLICATION;

/**
 * Base interface for all internal system transaction and application transaction.
 */
public interface Transaction extends SerializableWithKnownLength {
	/**
	 * Internal use accessor that returns a flag indicating whether this is a system transaction.
	 *
	 * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
	 * 		transaction
	 */
	default boolean isSystem() {
		return getTransactionType() != APPLICATION;
	}
	/**
	 * @return transaction type
	 */
	TransactionType getTransactionType();

	/**
	 * Get the size of the transaction
	 * @return
	 * 		the size of the transaction in the unit of byte
	 */
	int getSize();
}

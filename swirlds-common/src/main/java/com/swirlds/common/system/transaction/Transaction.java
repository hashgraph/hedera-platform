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

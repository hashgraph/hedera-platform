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

package com.swirlds.common.system.address;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This class provides methods for validating new address books. These methods do not throw exceptions if
 * validation fails, but they are intentionally very noisy in the logs when they fail.
 */
public final class AddressBookValidator {

	private static final Logger LOG = LogManager.getLogger(AddressBookValidator.class);

	private AddressBookValidator() {

	}

	// FUTURE WORK: this class is only partially completed. Additional validation will be added before changing address
	// books at runtime is fully supported.

	/**
	 * Make sure the address book has at least some stake.
	 *
	 * @param addressBook
	 * 		the address book to validate
	 * @return if the address book passes this validation
	 */
	public static boolean hasNonZeroStake(final AddressBook addressBook) {
		if (addressBook.getTotalStake() <= 0) {
			LOG.error(EXCEPTION.getMarker(), "address book for round {} has {} total stake",
					addressBook.getRound(), addressBook.getTotalStake());
			return false;
		}
		return true;
	}

	/**
	 * Make sure the address book is not empty.
	 *
	 * @param addressBook
	 * 		the address book to validate
	 * @return if the address book passes this validation
	 */
	public static boolean isNonEmpty(final AddressBook addressBook) {
		if (addressBook.getSize() == 0) {
			LOG.error(EXCEPTION.getMarker(), "address book for round {} is empty", addressBook.getRound());
			return false;
		}
		return true;
	}

	/**
	 * Sanity check, make sure that the next address book has a next ID greater or equal to the previous one.
	 *
	 * @param previousAddressBook
	 * 		the previous address book
	 * @param addressBook
	 * 		the address book to validate
	 * @return if the address book passes this validation
	 */
	public static boolean validNextId(
			final AddressBook previousAddressBook,
			final AddressBook addressBook) {

		if (previousAddressBook.getNextNodeId() > addressBook.getNextNodeId()) {
			LOG.error(EXCEPTION.getMarker(),
					"Invalid next node ID. Previous address book has a next node ID of {}, " +
							"new address book has a next node ID of {}",
					previousAddressBook.getNextNodeId(), addressBook.getNextNodeId());
			return false;
		}

		return true;
	}

	/**
	 * No address that is removed may be re-added to the address book. If address N is skipped and address N+1 is later
	 * added, then address N can never be added (as this is difficult to distinguish from
	 * N being added and then removed).
	 *
	 * @param previousAddressBook
	 * 		the previous address book
	 * @param addressBook
	 * 		the address book to validate
	 * @return if the address book passes this validation
	 */
	public static boolean noAddressReinsertion(
			final AddressBook previousAddressBook,
			final AddressBook addressBook) {

		final long previousNextId = previousAddressBook.getNextNodeId();
		for (final Address address : addressBook) {
			final long nodeId = address.getId();
			if (nodeId < previousNextId && !previousAddressBook.contains(nodeId)) {
				LOG.error(EXCEPTION.getMarker(), "Once an address is removed or a node ID is skipped, " +
						"an address with that some node ID may never be added again. Invalid node ID = {}", nodeId);
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if a genesis address book is valid. In the case of an invalid address book, this method will
	 * write an error message to the log.
	 *
	 * @param candidateAddressBook
	 * 		a candidate address book that is being tested for validity
	 * @return true if the address book is valid
	 */
	public static boolean isGenesisAddressBookValid(final AddressBook candidateAddressBook) {

		return hasNonZeroStake(candidateAddressBook) &&
				isNonEmpty(candidateAddressBook);
	}

	/**
	 * Check if a new address book transition is valid. In the case of an invalid address book, this method will
	 * write an error message to the log.
	 *
	 * @param previousAddressBook
	 * 		the previous address book, is assumed to be valid
	 * @param candidateAddressBook
	 * 		the new address book that follows the current address book
	 * @return true if the transition is valid
	 */
	public static boolean isNextAddressBookValid(
			final AddressBook previousAddressBook,
			final AddressBook candidateAddressBook) {

		return hasNonZeroStake(candidateAddressBook) &&
				isNonEmpty(candidateAddressBook) &&
				validNextId(previousAddressBook, candidateAddressBook) &&
				noAddressReinsertion(previousAddressBook, candidateAddressBook);
	}
}

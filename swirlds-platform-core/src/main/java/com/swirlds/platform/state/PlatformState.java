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

package com.swirlds.platform.state;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.system.address.AddressBook;

/**
 * This subtree contains state data which is managed and used exclusively by the platform.
 */
public class PlatformState extends PartialBinaryMerkleInternal implements MerkleInternal {

	public static final long CLASS_ID = 0x483ae5404ad0d0bfL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private static final class ChildIndices {
		public static final int PLATFORM_DATA = 0;
		public static final int ADDRESS_BOOK = 1;
	}

	public PlatformState() {

	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the node to copy
	 */
	private PlatformState(final PlatformState that) {
		super(that);
		if (that.getPlatformData() != null) {
			this.setPlatformData(that.getPlatformData().copy());
		}
		if (that.getAddressBook() != null) {
			this.setAddressBook(that.getAddressBook().copy());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PlatformState copy() {
		return new PlatformState(this);
	}

	/**
	 * Get the address book.
	 */
	public AddressBook getAddressBook() {
		return getChild(ChildIndices.ADDRESS_BOOK);
	}

	/**
	 * Set the address book.
	 *
	 * @param addressBook
	 * 		an address book
	 */
	public void setAddressBook(final AddressBook addressBook) {
		setChild(ChildIndices.ADDRESS_BOOK, addressBook);
	}

	/**
	 * Get the object containing miscellaneous round information.
	 *
	 * @return round data
	 */
	public PlatformData getPlatformData() {
		return getChild(ChildIndices.PLATFORM_DATA);
	}

	/**
	 * Set the object containing miscellaneous platform information.
	 *
	 * @param round
	 * 		round data
	 */
	public void setPlatformData(final PlatformData round) {
		setChild(ChildIndices.PLATFORM_DATA, round);
	}

	/**
	 * Generates a one-line summary of important fields from the <code>PlatformState</code>, meant to be logged at
	 * the
	 * same time as a call to <code>MerkleHashChecker.generateHashDebugString()</code>.
	 */
	public String getInfoString() {
		final StringBuilder sb = new StringBuilder();

		final PlatformData stateInfo = getPlatformData();

		sb.append("Round = ").append(stateInfo.getRound());
		sb.append(", number of consensus events = ").append(stateInfo.getNumEventsCons());
		sb.append(", consensus timestamp = ").append(stateInfo.getConsensusTimestamp());
		sb.append(", last timestamp = ").append(stateInfo.getLastTransactionTimestamp());
		sb.append(", consensus Events running hash = ").append(stateInfo.getHashEventsCons());
		sb.append(", address book hash = ").append(getAddressBook().getHash());
		return sb.toString();
	}
}

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

package com.swirlds.common.test.state;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;


/**
 * A dummy swirld state for SignedStateManager unit tests.
 */
public class DummySwirldState2 extends AbstractDummySwirldState implements SwirldState2 {

	// The version history of this class.
	// Versions that have been released must NEVER be given a different value.
	/**
	 * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
	 * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
	 * specially by the platform.
	 */
	private static final int VERSION_ORIGINAL = 1;
	/**
	 * In this version, serialization was performed by serialize/deserialize.
	 */
	private static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;

	private static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

	private static final long CLASS_ID = 0xa7d6e4b5feda7ce5L;

	private CountDownLatch serializationLatch;

	public DummySwirldState2() {
		super();
	}

	public DummySwirldState2(final AddressBook addressBook) {
		super();
		this.addressBook = addressBook;
	}

	/**
	 * Protection should always be enabled but current unit tests don't expect this behavior
	 *
	 * @param protectionEnabled
	 * 		If protection is enabled then this SignedState can only be deleted/archived after explicitly enabled.
	 */
	public DummySwirldState2(final boolean protectionEnabled) {
		super(protectionEnabled);
	}

	private DummySwirldState2(final DummySwirldState2 that) {
		super(that);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AddressBook getAddressBookCopy() {
		return super.getAddressBookCopy();
	}

	@Override
	public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
		// intentionally does nothing
	}

	@Override
	public boolean isArchived() {
		return archived;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DummySwirldState2 copy() {
		throwIfImmutable();
		return new DummySwirldState2(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void archive() {
		super.archive();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof final DummySwirldState2 that)) {
			return false;
		}
		return Objects.equals(this.addressBook, that.addressBook);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return 0;
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
		return CLASS_VERSION;
	}

	/**
	 * If called, the next serialization attempt will block until {@link #unblockSerialization()} has been called.
	 */
	public void enableBlockingSerialization() {
		serializationLatch = new CountDownLatch(1);
	}

	/**
	 * Should only be called if {@link #enableBlockingSerialization()} has previously been called.
	 */
	public void unblockSerialization() {
		serializationLatch.countDown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		if (serializationLatch != null) {
			abortAndThrowIfInterrupted(serializationLatch::await,
					"interrupted while waiting for latch");
		}
	}
}

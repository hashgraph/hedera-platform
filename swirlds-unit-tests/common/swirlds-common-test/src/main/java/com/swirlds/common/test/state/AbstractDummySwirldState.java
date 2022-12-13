/*
 * Copyright (C) 2016-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swirlds.common.test.state;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.address.AddressBook;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractDummySwirldState extends PartialMerkleLeaf implements MerkleLeaf {

    private static final long DEFAULT_UNIT_TEST_SECS = 10;

    protected volatile boolean allowDeletion;
    protected volatile boolean allowArchiving;

    protected volatile boolean archived = false;

    protected AddressBook addressBook;

    protected CountDownLatch deletionLatch = new CountDownLatch(1);
    protected CountDownLatch archivalLatch = new CountDownLatch(1);

    protected AtomicBoolean released = new AtomicBoolean(false);

    private Hash hashOverride;

    protected AbstractDummySwirldState() {
        this(false);
    }

    /**
     * protection should always be enabled but current unit tests don't expect this behavior
     *
     * @param protectionEnabled If protection is enabled then this SignedState can only be
     *     deleted/archived after explicitly enabled.
     */
    protected AbstractDummySwirldState(boolean protectionEnabled) {
        allowDeletion = !protectionEnabled;
        allowArchiving = !protectionEnabled;
    }

    protected AbstractDummySwirldState(final AbstractDummySwirldState that) {
        super(that);
        if (that.addressBook != null) {
            this.addressBook = that.addressBook.copy();
        }
        this.allowDeletion = that.allowDeletion;
        this.allowArchiving = that.allowArchiving;
        this.archived = that.archived;
        this.deletionLatch = that.deletionLatch;
        this.archivalLatch = that.archivalLatch;
        this.released = new AtomicBoolean(that.released.get());
    }

    /** {@inheritDoc} */
    @Override
    public Hash getHash() {
        if (hashOverride != null) {
            return hashOverride;
        } else {
            return super.getHash();
        }
    }

    /**
     * Set the hash of this object, overrides any hash that might be computed. Hash is not preserved
     * during serialization.
     */
    public void setHashOverride(final Hash hashOverride) {
        this.hashOverride = hashOverride;
    }

    /** Set the override hash of this object. */
    public Hash getHashOverride() {
        return hashOverride;
    }

    public void enableDeletion() {
        allowDeletion = true;
    }

    public void disableDeletion() {
        allowDeletion = false;
    }

    public void enableArchiving() {
        allowArchiving = true;
    }

    public void disableArchiving() {
        allowArchiving = false;
    }

    public void waitForDeletion() {
        try {
            // 10 seconds is assumed to be more than sufficient for any unit test. If a test
            // requires
            // a greater wait then a variable timeout parameter can be added.
            assertTrue(
                    deletionLatch.await(DEFAULT_UNIT_TEST_SECS, TimeUnit.SECONDS),
                    "Unit test took longer than the default of 10 seconds. Fix the test or override"
                            + " the wait time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        }
    }

    public void waitForArchival() {
        try {
            // 10 seconds is assumed to be more than sufficient for any unit test. If a test
            // requires
            // a greater wait then a variable timeout parameter can be added.
            assertTrue(
                    archivalLatch.await(DEFAULT_UNIT_TEST_SECS, TimeUnit.SECONDS),
                    "Unit test took longer than the default of 10 seconds. Fix the test or override"
                            + " the wait time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        }
    }

    protected AddressBook getAddressBookCopy() {
        if (addressBook == null) {
            return null;
        } else {
            return addressBook.copy();
        }
    }

    public void setAddressBook(AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    /** {@inheritDoc} */
    @Override
    public void destroyNode() {
        if (!allowDeletion) {
            fail("State is not allowed to be deleted");
        }
        if (!released.compareAndSet(false, true)) {
            throw new IllegalStateException("This type of node should only be deleted once");
        }
        deletionLatch.countDown();
        archived =
                true; // Delete supersedes archive, and once deleted archive() will never be called.
        archivalLatch.countDown();
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        // intentionally does nothing
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        // intentionally does nothing
    }

    protected void archive() {
        if (!allowArchiving) {
            fail("State is not allowed to be archived");
        }
        if (archived) {
            fail("State has already been archived");
        }
        archived = true;
        archivalLatch.countDown();
    }
}

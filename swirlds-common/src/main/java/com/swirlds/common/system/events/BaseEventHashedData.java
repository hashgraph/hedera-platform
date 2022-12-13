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
package com.swirlds.common.system.events;

import static com.swirlds.common.io.streams.SerializableDataOutputStream.getSerializedLength;

import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A class used to store base event data that is used to create the hash of that event.
 *
 * <p>A base event is a set of data describing an event at the point when it is created, before it
 * is added to the hashgraph and before its consensus can be determined. Some of this data is used
 * to create a hash of an event and some data is additional and does not affect the hash. This data
 * is split into 2 classes: {@link BaseEventHashedData} and {@link BaseEventUnhashedData}.
 */
public class BaseEventHashedData extends AbstractSerializableHashable
        implements OptionalSelfSerializable<EventSerializationOptions> {
    public static final int TO_STRING_BYTE_ARRAY_LENGTH = 5;
    private static final long CLASS_ID = 0x21c2620e9b6a2243L;

    private static class ClassVersion {
        /**
         * In this version, the transactions contained by this event are encoded using
         * LegacyTransaction class. No longer supported.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, the transactions contained by this event are encoded using a newer
         * version Transaction class with different subclasses to support internal system
         * transactions and application transactions
         */
        public static final int TRANSACTION_SUBCLASSES = 2;
    }
    ///////////////////////////////////////
    // immutable, sent during normal syncs, affects the hash that is signed:
    ///////////////////////////////////////

    /** ID of this event's creator (translate before sending) */
    private long creatorId;
    /** the generation for the self parent */
    private long selfParentGen;
    /** the generation for the other parent */
    private long otherParentGen;
    /** self parent hash value */
    private Hash selfParentHash;
    /** other parent hash value */
    private Hash otherParentHash;
    /** creation time, as claimed by its creator */
    private Instant timeCreated;
    /** the payload: an array of transactions */
    private ConsensusTransactionImpl[] transactions;

    /** are any of the transactions user transactions */
    private boolean hasUserTransactions;

    public BaseEventHashedData() {}

    /**
     * Create a BaseEventHashedData object
     *
     * @param creatorId ID of this event's creator
     * @param selfParentGen the generation for the self parent
     * @param otherParentGen the generation for the other parent
     * @param selfParentHash self parent hash value
     * @param otherParentHash other parent hash value
     * @param timeCreated creation time, as claimed by its creator
     * @param transactions the payload: an array of transactions included in this event instance
     */
    public BaseEventHashedData(
            final long creatorId,
            final long selfParentGen,
            final long otherParentGen,
            final Hash selfParentHash,
            final Hash otherParentHash,
            final Instant timeCreated,
            final ConsensusTransactionImpl[] transactions) {
        this.creatorId = creatorId;
        this.selfParentGen = selfParentGen;
        this.otherParentGen = otherParentGen;
        this.selfParentHash = selfParentHash;
        this.otherParentHash = otherParentHash;
        this.timeCreated = timeCreated;
        this.transactions = transactions;
        checkUserTransactions();
    }

    /**
     * Create a BaseEventHashedData object
     *
     * @param creatorId ID of this event's creator
     * @param selfParentGen the generation for the self parent
     * @param otherParentGen the generation for the other parent
     * @param selfParentHash self parent hash value in byte array format
     * @param otherParentHash other parent hash value in byte array format
     * @param timeCreated creation time, as claimed by its creator
     * @param transactions the payload: an array of transactions included in this event instance
     */
    public BaseEventHashedData(
            final long creatorId,
            final long selfParentGen,
            final long otherParentGen,
            final byte[] selfParentHash,
            final byte[] otherParentHash,
            final Instant timeCreated,
            final ConsensusTransactionImpl[] transactions) {
        this(
                creatorId,
                selfParentGen,
                otherParentGen,
                selfParentHash == null ? null : new Hash(selfParentHash),
                otherParentHash == null ? null : new Hash(otherParentHash),
                timeCreated,
                transactions);
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.TRANSACTION_SUBCLASSES;
    }

    @Override
    public void serialize(
            final SerializableDataOutputStream out, final EventSerializationOptions option)
            throws IOException {
        out.writeLong(creatorId);
        out.writeLong(selfParentGen);
        out.writeLong(otherParentGen);
        out.writeSerializable(selfParentHash, false);
        out.writeSerializable(otherParentHash, false);
        out.writeInstant(timeCreated);

        // write serialized length of transaction array first, so during the deserialization proces
        // it is possible to skip transaction array and move on to the next object
        if (option == EventSerializationOptions.OMIT_TRANSACTIONS) {
            out.writeInt(getSerializedLength(null, true, false));
            out.writeSerializableArray(null, true, false);
        } else {
            out.writeInt(getSerializedLength(transactions, true, false));
            // transactions may include both system transactions and application transactions
            // so writeClassId set to true and allSameClass set to false
            out.writeSerializableArray(transactions, true, false);
        }
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serialize(out, EventSerializationOptions.FULL);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        deserialize(in, version, SettingsCommon.maxTransactionCountPerEvent);
    }

    public void deserialize(
            final SerializableDataInputStream in, final int version, final int maxTransactionCount)
            throws IOException {
        creatorId = in.readLong();
        selfParentGen = in.readLong();
        otherParentGen = in.readLong();
        selfParentHash = in.readSerializable(false, Hash::new);
        otherParentHash = in.readSerializable(false, Hash::new);
        timeCreated = in.readInstant();
        if (version == ClassVersion.TRANSACTION_SUBCLASSES) {
            in.readInt(); // read serialized length
            transactions =
                    in.readSerializableArray(
                            ConsensusTransactionImpl[]::new, maxTransactionCount, true);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported version "
                            + version
                            + ". Minimum supported version is "
                            + getMinimumSupportedVersion());
        }
        checkUserTransactions();
    }

    /** Check if array of transactions has any user created transaction inside */
    private void checkUserTransactions() {
        if (transactions != null) {
            for (final ConsensusTransaction t : getTransactions()) {
                if (!t.isSystem()) {
                    hasUserTransactions = true;
                    break;
                }
            }
        }
    }

    public boolean hasUserTransactions() {
        return hasUserTransactions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BaseEventHashedData that = (BaseEventHashedData) o;

        return (creatorId == that.creatorId)
                && (selfParentGen == that.selfParentGen)
                && (otherParentGen == that.otherParentGen)
                && Objects.equals(selfParentHash, that.selfParentHash)
                && Objects.equals(otherParentHash, that.otherParentHash)
                && Objects.equals(timeCreated, that.timeCreated)
                && Arrays.equals(transactions, that.transactions);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(creatorId)
                .append(selfParentGen)
                .append(otherParentGen)
                .append(selfParentHash)
                .append(otherParentHash)
                .append(timeCreated)
                .append(transactions)
                .toHashCode();
    }

    @Override
    public String toString() {
        return "BaseEventHashedData{"
                + "creatorId="
                + creatorId
                + ", selfParentGen="
                + selfParentGen
                + ", otherParentGen="
                + otherParentGen
                + ", selfParentHash="
                + CommonUtils.hex(valueOrNull(selfParentHash), TO_STRING_BYTE_ARRAY_LENGTH)
                + ", otherParentHash="
                + CommonUtils.hex(valueOrNull(otherParentHash), TO_STRING_BYTE_ARRAY_LENGTH)
                + ", timeCreated="
                + timeCreated
                + ", transactions size="
                + (transactions == null ? "null" : transactions.length)
                + ", hash="
                + CommonUtils.hex(valueOrNull(getHash()), TO_STRING_BYTE_ARRAY_LENGTH)
                + '}';
    }

    private byte[] valueOrNull(final Hash hash) {
        return hash == null ? null : hash.getValue();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.TRANSACTION_SUBCLASSES;
    }

    public long getCreatorId() {
        return creatorId;
    }

    public long getSelfParentGen() {
        return selfParentGen;
    }

    public long getOtherParentGen() {
        return otherParentGen;
    }

    public Hash getSelfParentHash() {
        return selfParentHash;
    }

    public Hash getOtherParentHash() {
        return otherParentHash;
    }

    public boolean hasSelfParent() {
        return selfParentHash != null;
    }

    public boolean hasOtherParent() {
        return otherParentHash != null;
    }

    public byte[] getSelfParentHashValue() {
        return selfParentHash == null ? null : selfParentHash.getValue();
    }

    public byte[] getOtherParentHashValue() {
        return otherParentHash == null ? null : otherParentHash.getValue();
    }

    public Instant getTimeCreated() {
        return timeCreated;
    }

    /**
     * @return array of transactions inside this event instance
     */
    public ConsensusTransactionImpl[] getTransactions() {
        return transactions;
    }

    public long getGeneration() {
        return calculateGeneration(selfParentGen, otherParentGen);
    }

    /**
     * Calculates the generation of an event based on its parents generations
     *
     * @param selfParentGeneration the generation of the self parent
     * @param otherParentGeneration the generation of the other parent
     * @return the generation of the event
     */
    public static long calculateGeneration(
            final long selfParentGeneration, final long otherParentGeneration) {
        return 1 + Math.max(selfParentGeneration, otherParentGeneration);
    }
}

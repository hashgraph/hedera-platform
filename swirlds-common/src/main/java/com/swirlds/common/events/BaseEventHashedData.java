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

package com.swirlds.common.events;

import com.swirlds.common.CommonUtils;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.transaction.internal.LegacyTransaction;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.time.Instant;

import static com.swirlds.common.io.SerializableDataOutputStream.getSerializedLength;

/**
 * A class used to store base event data that is used to create the hash of that event.
 * <p>
 * A base event is a set of data describing an event at the point when it is created, before it is added to the
 * hashgraph and before its consensus can be determined. Some of this data is used to create a hash of an event
 * and some data is additional and does not affect the hash. This data is split into 2 classes:
 * {@link BaseEventHashedData} and {@link BaseEventUnhashedData}.
 */
public class BaseEventHashedData extends AbstractSerializableHashable
		implements OptionalSelfSerializable<EventSerializationOptions> {
	public static final int TO_STRING_BYTE_ARRAY_LENGTH = 5;
	private static final long CLASS_ID = 0x21c2620e9b6a2243L;

	private static class ClassVersion {
		/**
		 * In this version, the transactions contained by this event are encoded using
		 * {@link com.swirlds.common.transaction.internal.LegacyTransaction} class.
		 */
		public static final int ORIGINAL = 1;
		/**
		 * In this version, the transactions contained by this event are encoded using a newer version Transaction
		 * class with different subclasses to support internal system transactions and application transactions
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
	private Transaction[] transactions;

	/** are any of the transactions user transactions */
	private boolean hasUserTransactions;

	public BaseEventHashedData() {
	}

	/**
	 * Create a BaseEventHashedData object
	 *
	 * @param creatorId
	 * 		ID of this event's creator
	 * @param selfParentGen
	 * 		the generation for the self parent
	 * @param otherParentGen
	 * 		the generation for the other parent
	 * @param selfParentHash
	 * 		self parent hash value
	 * @param otherParentHash
	 * 		other parent hash value
	 * @param timeCreated
	 * 		creation time, as claimed by its creator
	 * @param transactions
	 * 		the payload: an array of transactions included in this event instance
	 */
	public BaseEventHashedData(long creatorId, long selfParentGen, long otherParentGen,
			Hash selfParentHash, Hash otherParentHash, Instant timeCreated, Transaction[] transactions) {
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
	 * @param creatorId
	 * 		ID of this event's creator
	 * @param selfParentGen
	 * 		the generation for the self parent
	 * @param otherParentGen
	 * 		the generation for the other parent
	 * @param selfParentHash
	 * 		self parent hash value in byte array format
	 * @param otherParentHash
	 * 		other parent hash value in byte array format
	 * @param timeCreated
	 * 		creation time, as claimed by its creator
	 * @param transactions
	 * 		the payload: an array of transactions included in this event instance
	 */
	public BaseEventHashedData(long creatorId, long selfParentGen, long otherParentGen,
			byte[] selfParentHash, byte[] otherParentHash, Instant timeCreated, Transaction[] transactions) {
		this(
				creatorId,
				selfParentGen,
				otherParentGen,
				selfParentHash == null ? null : new Hash(selfParentHash),
				otherParentHash == null ? null : new Hash(otherParentHash),
				timeCreated,
				transactions
		);
	}

	@Override
	public void serialize(SerializableDataOutputStream out, EventSerializationOptions option) throws IOException {
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
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serialize(out, EventSerializationOptions.FULL);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		deserialize(in, version, SettingsCommon.maxTransactionCountPerEvent);
	}

	public void deserialize(SerializableDataInputStream in, int version, int maxTransactionCount) throws IOException {
		creatorId = in.readLong();
		selfParentGen = in.readLong();
		otherParentGen = in.readLong();
		selfParentHash = in.readSerializable(false, Hash::new);
		otherParentHash = in.readSerializable(false, Hash::new);
		timeCreated = in.readInstant();
		if (version == ClassVersion.TRANSACTION_SUBCLASSES) {
			in.readInt(); //read serialized length
			transactions = in.readSerializableArray(
					Transaction[]::new,
					maxTransactionCount,
					true);
		} else if (version == ClassVersion.ORIGINAL) {
			// read legacy version of transaction then convert to swirldTransaction
			transactions = in.readSerializableArray(
					LegacyTransaction[]::new,
					maxTransactionCount,
					false,
					LegacyTransaction::new);
		} else {
			throw new UnsupportedOperationException("Unsupported version " + version);
		}
		checkUserTransactions();
	}


	/**
	 * Check if array of transactions has any user created transaction inside
	 */
	private void checkUserTransactions() {
		if (transactions != null) {
			for (Transaction t : getTransactions()) {
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

		return new EqualsBuilder()
				.append(creatorId, that.creatorId)
				.append(selfParentGen, that.selfParentGen)
				.append(otherParentGen, that.otherParentGen)
				.append(selfParentHash, that.selfParentHash)
				.append(otherParentHash, that.otherParentHash)
				.append(timeCreated, that.timeCreated)
				.append(transactions, that.transactions)
				.isEquals();
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
		return "BaseEventHashedData{" +
				"creatorId=" + creatorId +
				", selfParentGen=" + selfParentGen +
				", otherParentGen=" + otherParentGen +
				", selfParentHash=" + CommonUtils.hex(valueOrNull(selfParentHash), TO_STRING_BYTE_ARRAY_LENGTH) +
				", otherParentHash=" + CommonUtils.hex(valueOrNull(otherParentHash), TO_STRING_BYTE_ARRAY_LENGTH) +
				", timeCreated=" + timeCreated +
				", transactions size=" + (transactions == null ? "null" : transactions.length) +
				", hash=" + CommonUtils.hex(valueOrNull(getHash()), TO_STRING_BYTE_ARRAY_LENGTH) +
				'}';
	}

	private byte[] valueOrNull(Hash hash) {
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
	public Transaction[] getTransactions() {
		return transactions;
	}

	public long getGeneration() {
		return calculateGeneration(selfParentGen, otherParentGen);
	}

	/**
	 * Calculates the generation of an event based on its parents generations
	 *
	 * @param selfParentGeneration
	 * 		the generation of the self parent
	 * @param otherParentGeneration
	 * 		the generation of the other parent
	 * @return the generation of the event
	 */
	public static long calculateGeneration(
			final long selfParentGeneration,
			final long otherParentGeneration) {
		return 1 + Math.max(selfParentGeneration, otherParentGeneration);
	}
}

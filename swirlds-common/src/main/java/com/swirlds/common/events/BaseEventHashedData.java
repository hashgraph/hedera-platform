/*
 * (c) 2016-2020 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.common.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.time.Instant;

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
	private static final int CLASS_VERSION = 1;

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
	@JsonIgnore
	private Transaction[] transactions;

	/** are any of the transactions user transactions */
	private boolean hasUserTransactions;

	public BaseEventHashedData() {
	}

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
		if (option == EventSerializationOptions.OMIT_TRANSACTIONS) {
			out.writeSerializableArray(null, false, true);
		} else {
			out.writeSerializableArray(transactions, false, true);
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
		transactions =
				in.readSerializableArray(
						Transaction[]::new,
						maxTransactionCount,
						false,
						Transaction::new);
		checkUserTransactions();
	}

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
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

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
		return CLASS_VERSION;
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

	public byte[] getSelfParentHashValue() {
		return selfParentHash == null ? null : selfParentHash.getValue();
	}

	public byte[] getOtherParentHashValue() {
		return otherParentHash == null ? null : otherParentHash.getValue();
	}

	public Instant getTimeCreated() {
		return timeCreated;
	}

	public Transaction[] getTransactions() {
		return transactions;
	}
}

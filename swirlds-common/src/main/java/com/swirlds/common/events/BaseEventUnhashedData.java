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

import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.Arrays;

/**
 * A class used to store base event data that does not affect the hash of that event.
 * <p>
 * A base event is a set of data describing an event at the point when it is created, before it is added to the
 * hashgraph and before its consensus can be determined. Some of this data is used to create a hash of an event
 * that is signed, and some data is additional and does not affect that hash. This data is split into 2 classes:
 * {@link BaseEventHashedData} and {@link BaseEventUnhashedData}.
 */
public class BaseEventUnhashedData implements SelfSerializable {
	private static final long CLASS_ID = 0x33cb9d4ae38c9e91L;
	private static final int CLASS_VERSION = 1;
	private static final int MAX_SIG_LENGTH = 384;

	///////////////////////////////////////
	// immutable, sent during normal syncs, does NOT affect the hash that is signed:
	///////////////////////////////////////

	/** sequence number for this by its creator (0 is first) */
	private long creatorSeq;
	/** ID of otherParent (translate before sending) */
	private long otherId;
	/** sequence number for otherParent event (by its creator) */
	private long otherSeq;
	/** creator's sig for this */
	private byte[] signature;

	public BaseEventUnhashedData() {
	}

	public BaseEventUnhashedData(long creatorSeq, long otherId, long otherSeq, byte[] signature) {
		this.creatorSeq = creatorSeq;
		this.otherId = otherId;
		this.otherSeq = otherSeq;
		this.signature = signature;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(creatorSeq);
		out.writeLong(otherId);
		out.writeLong(otherSeq);
		out.writeByteArray(signature);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		creatorSeq = in.readLong();
		otherId = in.readLong();
		otherSeq = in.readLong();
		signature = in.readByteArray(MAX_SIG_LENGTH);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final BaseEventUnhashedData that = (BaseEventUnhashedData) o;

		return new EqualsBuilder()
				.append(creatorSeq, that.creatorSeq)
				.append(otherId, that.otherId)
				.append(otherSeq, that.otherSeq)
				.append(signature, that.signature)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(creatorSeq)
				.append(otherId)
				.append(otherSeq)
				.append(signature)
				.toHashCode();
	}

	@Override
	public String toString() {
		return "BaseEventUnhashedData{" +
				"creatorSeq=" + creatorSeq +
				", otherId=" + otherId +
				", otherSeq=" + otherSeq +
				", signature=" + CommonUtils.hex(signature, 5) +
				'}';
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	public long getCreatorSeq() {
		return creatorSeq;
	}

	public long getOtherId() {
		return otherId;
	}

	public long getOtherSeq() {
		return otherSeq;
	}

	public byte[] getSignature() {
		return signature;
	}
}

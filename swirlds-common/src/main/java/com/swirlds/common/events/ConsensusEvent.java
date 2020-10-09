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

import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;

public class ConsensusEvent extends AbstractSerializableHashable
		implements OptionalSelfSerializable<EventSerializationOptions> {

	public static final long CLASS_ID = 0xe250a9fbdcc4b1baL;
	public static final int CLASS_VERSION = 1;

	/** The hashed part of a base event */
	private BaseEventHashedData baseEventHashedData;
	/** The part of a base event which is not hashed */
	private BaseEventUnhashedData baseEventUnhashedData;
	/** Consensus data calculated for an event */
	private ConsensusData consensusData;

	public ConsensusEvent() {
	}

	public ConsensusEvent(BaseEventHashedData baseEventHashedData,
			BaseEventUnhashedData baseEventUnhashedData, ConsensusData consensusData) {
		this.baseEventHashedData = baseEventHashedData;
		this.baseEventUnhashedData = baseEventUnhashedData;
		this.consensusData = consensusData;
	}

	@Override
	public void serialize(SerializableDataOutputStream out, EventSerializationOptions option) throws IOException {
		serialize(out, baseEventHashedData, baseEventUnhashedData, consensusData, option);
	}

	public static void serialize(SerializableDataOutputStream out,
			BaseEventHashedData baseEventHashedData,
			BaseEventUnhashedData baseEventUnhashedData,
			ConsensusData consensusData,
			EventSerializationOptions option) throws IOException {
		out.writeOptionalSerializable(baseEventHashedData, false, option);
		out.writeSerializable(baseEventUnhashedData, false);
		out.writeSerializable(consensusData, false);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serialize(out, baseEventHashedData, baseEventUnhashedData, consensusData, EventSerializationOptions.FULL);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		baseEventHashedData = in.readSerializable(false, BaseEventHashedData::new);
		baseEventUnhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
		consensusData = in.readSerializable(false, ConsensusData::new);
	}

	public BaseEventHashedData getBaseEventHashedData() {
		return baseEventHashedData;
	}

	public BaseEventUnhashedData getBaseEventUnhashedData() {
		return baseEventUnhashedData;
	}

	public ConsensusData getConsensusData() {
		return consensusData;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(baseEventHashedData.hashCode())
				.append(baseEventUnhashedData.hashCode())
				.append(consensusData.hashCode()).build();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != getClass()) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		ConsensusEvent that = (ConsensusEvent) obj;
		return new EqualsBuilder()
				.append(this.baseEventHashedData, that.baseEventHashedData)
				.append(this.baseEventUnhashedData, that.baseEventUnhashedData)
				.append(this.consensusData, that.consensusData)
				.isEquals();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("baseEventHashedData", baseEventHashedData)
				.append("baseEventUnhashedData", baseEventUnhashedData)
				.append("consensusData", consensusData)
				.toString();
	}
}

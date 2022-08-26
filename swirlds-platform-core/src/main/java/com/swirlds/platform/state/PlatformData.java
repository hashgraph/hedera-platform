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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.events.EventSerializationOptions;
import com.swirlds.platform.EventImpl;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A collection of miscellaneous platform data.
 */
public class PlatformData extends PartialMerkleLeaf implements MerkleLeaf {

	private static final long CLASS_ID = 0x1f89d0c43a8c08bdL;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	/**
	 * The round number for the genesis state.
	 */
	public static final long GENESIS_ROUND_NUMBER = 1;

	/**
	 * The round of this state. When this state becomes a SignedState, it will reflect all modifications from
	 * all transactions that have reached consensus in a round less than or equal to this value.
	 */
	private long round;

	/**
	 * how many consensus events have there been throughout all of history, up through the round received
	 * that this SignedState represents.
	 */
	private long numEventsCons;

	/**
	 * running hash of the hashes of all consensus events have there been throughout all of history, up
	 * through the round received that this SignedState represents.
	 */
	private Hash hashEventsCons;

	/**
	 * contains events for the round that is being signed and the preceding rounds
	 */
	private EventImpl[] events;

	/**
	 * the consensus timestamp for this signed state
	 */
	private Instant consensusTimestamp;

	/**
	 * the minimum generation of famous witnesses per round
	 */
	private List<MinGenInfo> minGenInfo;

	/**
	 * the timestamp of the last transactions handled by this state
	 */
	private Instant lastTransactionTimestamp;

	/**
	 * The version of the application software that was responsible for creating this state.
	 */
	private SoftwareVersion creationSoftwareVersion;

	public PlatformData() {

	}

	/**
	 * Copy constructor.
	 *
	 * @param that
	 * 		the object to copy
	 */
	private PlatformData(final PlatformData that) {
		super(that);
		this.round = that.round;
		this.numEventsCons = that.numEventsCons;
		this.hashEventsCons = that.hashEventsCons;
		if (that.events != null) {
			this.events = Arrays.copyOf(that.events, that.events.length);
		}
		this.consensusTimestamp = that.consensusTimestamp;
		if (that.minGenInfo != null) {
			this.minGenInfo = new ArrayList<>(that.minGenInfo);
		}
		this.lastTransactionTimestamp = that.lastTransactionTimestamp;
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
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(round);
		out.writeLong(numEventsCons);
		out.writeSerializable(hashEventsCons, false);

		out.writeInt(events.length);
		for (EventImpl event : events) {
			out.writeOptionalSerializable(event, false, EventSerializationOptions.OMIT_TRANSACTIONS);
			out.writeSerializable(event.getBaseEventHashedData().getHash(), false);
		}
		out.writeInstant(consensusTimestamp);

		out.writeInt(minGenInfo.size());
		for (final MinGenInfo info : minGenInfo) {
			out.writeLong(info.round());
			out.writeLong(info.minimumGeneration());
		}
		out.writeInstant(lastTransactionTimestamp);
		out.writeSerializable(creationSoftwareVersion, true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		round = in.readLong();
		numEventsCons = in.readLong();

		hashEventsCons = in.readSerializable(false, Hash::new);

		int eventNum = in.readInt();
		events = new EventImpl[eventNum];
		for (int i = 0; i < eventNum; i++) {
			events[i] = in.readSerializable(false, EventImpl::new);
			events[i].getBaseEventHashedData().setHash(in.readSerializable(false, Hash::new));
			events[i].markAsSignedStateEvent();
		}

		consensusTimestamp = in.readInstant();


		final int minGenInfoSize = in.readInt();
		minGenInfo = new LinkedList<>();
		for (int i = 0; i < minGenInfoSize; i++) {
			minGenInfo.add(new MinGenInfo(in.readLong(), in.readLong()));
		}

		State.linkParents(events);

		lastTransactionTimestamp = in.readInstant();
		creationSoftwareVersion = in.readSerializable();
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
	public PlatformData copy() {
		return new PlatformData(this);
	}

	/**
	 * Get the software version of the application that created this state.
	 *
	 * @return the creation version
	 */
	public SoftwareVersion getCreationSoftwareVersion() {
		return creationSoftwareVersion;
	}

	/**
	 * Set the software version of the application that created this state.
	 *
	 * @param creationVersion
	 * 		the creation version
	 * @return this object
	 */
	public PlatformData setCreationSoftwareVersion(final SoftwareVersion creationVersion) {
		this.creationSoftwareVersion = creationVersion;
		return this;
	}


	/**
	 * Get the round when this state was generated.
	 *
	 * @return a round number
	 */
	public long getRound() {
		return round;
	}

	/**
	 * Set the round when this state was generated.
	 *
	 * @param round
	 * 		a round number
	 * @return this object
	 */
	public PlatformData setRound(final long round) {
		this.round = round;
		return this;
	}

	/**
	 * Get the number of consensus events that have been applied to this state since the beginning of time.
	 *
	 * @return the number of handled consensus events
	 */
	public long getNumEventsCons() {
		return numEventsCons;
	}

	/**
	 * Set the number of consensus events that have been applied to this state since the beginning of time.
	 *
	 * @param numEventsCons
	 * 		the number of handled consensus events
	 * @return this object
	 */
	public PlatformData setNumEventsCons(final long numEventsCons) {
		this.numEventsCons = numEventsCons;
		return this;
	}

	/**
	 * Get the running hash of all events that have been applied to this state since the begining of time.
	 *
	 * @return a running hash of events
	 */
	public Hash getHashEventsCons() {
		return hashEventsCons;
	}

	/**
	 * Set the running hash of all events that have been applied to this state since the begining of time.
	 *
	 * @param hashEventsCons
	 * 		a running hash of events
	 * @return this object
	 */
	public PlatformData setHashEventsCons(final Hash hashEventsCons) {
		this.hashEventsCons = hashEventsCons;
		return this;
	}

	/**
	 * Get the events stored in this state.
	 *
	 * @return an array of events
	 */
	public EventImpl[] getEvents() {
		return events;
	}

	/**
	 * Set the events stored in this state.
	 *
	 * @param events
	 * 		an array of events
	 * @return this object
	 */
	public PlatformData setEvents(final EventImpl[] events) {
		this.events = events;
		return this;
	}

	/**
	 * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was
	 * applied in the round that created the state.
	 *
	 * @return a consensus timestamp
	 */
	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	/**
	 * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was
	 * applied in the round that created the state.
	 *
	 * @param consensusTimestamp
	 * 		a consensus timestamp
	 * @return this object
	 */
	public PlatformData setConsensusTimestamp(final Instant consensusTimestamp) {
		this.consensusTimestamp = consensusTimestamp;
		return this;
	}

	/**
	 * Get the minimum event generation for each node within this state.
	 *
	 * @return minimum generation info list
	 */
	public List<MinGenInfo> getMinGenInfo() {
		return minGenInfo;
	}

	/**
	 * Get the minimum event generation for each node within this state.
	 *
	 * @param minGenInfo
	 * 		minimum generation info list
	 * @return this object
	 */
	public PlatformData setMinGenInfo(final List<MinGenInfo> minGenInfo) {
		this.minGenInfo = minGenInfo;
		return this;
	}

	/**
	 * Get the timestamp of the last transaction that was applied during this round.
	 *
	 * @return a timestamp
	 */
	public Instant getLastTransactionTimestamp() {
		return lastTransactionTimestamp;
	}

	/**
	 * Set the timestamp of the last transaction that was applied during this round.
	 *
	 * @param lastTransactionTimestamp
	 * 		a timestamp
	 * @return this object
	 */
	public PlatformData setLastTransactionTimestamp(final Instant lastTransactionTimestamp) {
		this.lastTransactionTimestamp = lastTransactionTimestamp;
		return this;
	}

	/**
	 * Sets the LastTransactionTimestamp based on the events contained
	 *
	 * @return this object
	 */
	public PlatformData calculateLastTransactionTimestampFromEvents() {
		if (events != null && events.length > 0) {
			setLastTransactionTimestamp(
					events[events.length - 1].getLastTransTime()
			);
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final PlatformData that = (PlatformData) o;

		return new EqualsBuilder()
				.append(round, that.round)
				.append(numEventsCons, that.numEventsCons)
				.append(hashEventsCons, that.hashEventsCons)
				.append(events, that.events)
				.append(consensusTimestamp, that.consensusTimestamp)
				.append(minGenInfo, that.minGenInfo)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(round)
				.append(numEventsCons)
				.append(hashEventsCons)
				.append(events)
				.append(consensusTimestamp)
				.append(minGenInfo)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("round", round)
				.append("numEventsCons", numEventsCons)
				.append("hashEventsCons", hashEventsCons)
				.append("events", events)
				.append("consensusTimestamp", consensusTimestamp)
				.append("minGenInfo", minGenInfo)
				.toString();
	}
}

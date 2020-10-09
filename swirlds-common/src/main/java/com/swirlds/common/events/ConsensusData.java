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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.time.Instant;

/**
 * A class used to store consensus data about an event.
 * <p>
 * This data is available for an event only after consensus has been determined for it. When an event is initially
 * created, there is no consensus data for it. It does not affect the hash that is signed.
 */
public class ConsensusData implements SelfSerializable {
	private static final long CLASS_ID = 0xddf20b7ce114a711L;
	private static final int CLASS_VERSION = 1;

	/** generation (which is 1 plus max of parents' generations) */
	private long generation;
	/** the created round of this event (max of parents', plus either 0 or 1. 0 if not parents. -1 if neg infinity) */
	private long roundCreated;
	/** is this a witness? (is round > selfParent's round, or there is no self parent?) */
	private boolean isWitness;
	/** is this both a witness and the fame election is over? */
	private boolean isFamous;
	/** is there a consensus that this event is stale (no order, transactions ignored) */
	private boolean stale;
	/** the community's consensus timestamp for this event (or an estimate, if isConsensus==false) */
	private Instant consensusTimestamp;
	/* if isConsensus, round where >=1/2 famous see me */
	private long roundReceived;
	/** if isConsensus, then my order in history (0 first), else -1 */
	private long consensusOrder;
	/** is this event the last in consensus order of all those with the same received round? */
	private boolean lastInRoundReceived = false;

	public ConsensusData() {
		generation = -1;
		roundCreated = -1;
		roundReceived = -1;
		consensusOrder = -1;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(generation);
		out.writeLong(roundCreated);
		out.writeBoolean(isWitness);
		out.writeBoolean(isFamous);
		out.writeBoolean(stale);
		out.writeBoolean(lastInRoundReceived);
		out.writeInstant(consensusTimestamp);
		out.writeLong(roundReceived);
		out.writeLong(consensusOrder);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		generation = in.readLong();
		roundCreated = in.readLong();
		isWitness = in.readBoolean();
		isFamous = in.readBoolean();
		stale = in.readBoolean();
		lastInRoundReceived = in.readBoolean();
		consensusTimestamp = in.readInstant();
		roundReceived = in.readLong();
		consensusOrder = in.readLong();
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		final ConsensusData that = (ConsensusData) o;

		return new EqualsBuilder()
				.append(generation, that.generation)
				.append(roundCreated, that.roundCreated)
				.append(isWitness, that.isWitness)
				.append(isFamous, that.isFamous)
				.append(stale, that.stale)
				.append(roundReceived, that.roundReceived)
				.append(consensusOrder, that.consensusOrder)
				.append(consensusTimestamp, that.consensusTimestamp)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(generation)
				.append(roundCreated)
				.append(isWitness)
				.append(isFamous)
				.append(stale)
				.append(roundReceived)
				.append(consensusOrder)
				.append(consensusTimestamp)
				.toHashCode();
	}

	@Override
	public String toString() {
		return "ConsensusEventData{" +
				"generation=" + generation +
				", roundCreated=" + roundCreated +
				", isWitness=" + isWitness +
				", isFamous=" + isFamous +
				", stale=" + stale +
				", consensusTimestamp=" + consensusTimestamp +
				", roundReceived=" + roundReceived +
				", consensusOrder=" + consensusOrder +
				", lastInRoundReceived=" + lastInRoundReceived +
				'}';
	}

	public long getGeneration() {
		return generation;
	}

	public void setGeneration(long generation) {
		this.generation = generation;
	}

	public long getRoundCreated() {
		return roundCreated;
	}

	public void setRoundCreated(long roundCreated) {
		this.roundCreated = roundCreated;
	}

	public boolean isWitness() {
		return isWitness;
	}

	public void setWitness(boolean witness) {
		isWitness = witness;
	}

	public boolean isFamous() {
		return isFamous;
	}

	public void setFamous(boolean famous) {
		isFamous = famous;
	}

	public boolean isStale() {
		return stale;
	}

	public void setStale(boolean stale) {
		this.stale = stale;
	}

	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public void setConsensusTimestamp(Instant consensusTimestamp) {
		this.consensusTimestamp = consensusTimestamp;
	}

	public long getRoundReceived() {
		return roundReceived;
	}

	public void setRoundReceived(long roundReceived) {
		this.roundReceived = roundReceived;
	}

	public long getConsensusOrder() {
		return consensusOrder;
	}

	public void setConsensusOrder(long consensusOrder) {
		this.consensusOrder = consensusOrder;
	}

	public boolean isLastInRoundReceived() {
		return lastInRoundReceived;
	}

	public void setLastInRoundReceived(boolean lastInRoundReceived) {
		this.lastInRoundReceived = lastInRoundReceived;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CLASS_VERSION;
	}
}

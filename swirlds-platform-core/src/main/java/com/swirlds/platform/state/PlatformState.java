/*
 * (c) 2016-2022 Swirlds, Inc.
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

package com.swirlds.platform.state;

import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.events.EventSerializationOptions;
import com.swirlds.common.io.SelfSerializableByteSnapshot;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.Utilities;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * State written and used by the platform.
 */
public class PlatformState extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger();

	public static final long CLASS_ID = 0x5bcd37c8b3dd97f5L;

	private static final class ClassVersion {
		private static final int ORIGINAL = 1;
		private static final int ADD_LAST_TRANS_TIME = 2;
		/** modify hashEventsCons from byte array to Hash type */
		public static final int CONSENSUS_EVENT_RUNNING_HASH = 3;
		/** start using mingen when loading consensus */
		public static final int LOAD_MINGEN_INTO_CONSENSUS = 4;
	}

	/**
	 * Round number of the last round for which all the famous witnesses are known. The signed state is a
	 * function of all consensus events at the time this is created, but some of the events in this round
	 * and earlier rounds may not yet be consensus events.
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
	/** the address book at the moment of signing */
	private AddressBook addressBook;
	/** contains events for the round that is being signed and the preceding rounds */
	private EventImpl[] events;
	/** the consensus timestamp for this signed state */
	private Instant consensusTimestamp;
	/** the minimum generation of famous witnesses per round */
	private List<Pair<Long, Long>> minGenInfo;
	/** the timestamp of the last transactions handled by this state */
	private Instant lastTransactionTimestamp;
	/** a snapshot of the data in the leaf, used for debugging */
	private SelfSerializableByteSnapshot<PlatformState> snapshot;

	public PlatformState() {
		this.events = new EventImpl[0];
	}

	private PlatformState(final PlatformState that) {
		super(that);
		this.round = that.round;
		this.numEventsCons = that.numEventsCons;
		this.hashEventsCons = that.hashEventsCons;
		this.addressBook = that.addressBook;
		if (that.events != null) {
			this.events = Arrays.copyOf(that.events, that.events.length);
		}
		this.consensusTimestamp = that.consensusTimestamp;
		if (that.minGenInfo != null) {
			this.minGenInfo = new ArrayList<>(that.minGenInfo);
		}
		this.lastTransactionTimestamp = that.lastTransactionTimestamp;
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(round);
		out.writeLong(numEventsCons);
		out.writeSerializable(hashEventsCons, false);
		out.writeSerializable(addressBook, false);

		out.writeInt(events.length);
		for (EventImpl event : events) {
			out.writeOptionalSerializable(event, false, EventSerializationOptions.OMIT_TRANSACTIONS);
			out.writeSerializable(event.getBaseEventHashedData().getHash(), false);
		}
		out.writeInstant(consensusTimestamp);
		Utilities.writeList(minGenInfo, out, (pair, stream) -> {
			stream.writeLong(pair.getKey());
			stream.writeLong(pair.getValue());
		});
		out.writeInstant(lastTransactionTimestamp);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		round = in.readLong();
		numEventsCons = in.readLong();

		if (version < ClassVersion.CONSENSUS_EVENT_RUNNING_HASH) {
			hashEventsCons = new Hash(in.readByteArray(DigestType.SHA_384.digestLength()));
		} else {
			hashEventsCons = in.readSerializable(false, Hash::new);
		}

		addressBook = in.readSerializable(false, AddressBook::new);

		int eventNum = in.readInt();
		events = new EventImpl[eventNum];
		for (int i = 0; i < eventNum; i++) {
			events[i] = in.readSerializable(false, EventImpl::new);
			events[i].getBaseEventHashedData().setHash(in.readSerializable(false, Hash::new));
			events[i].markAsSignedStateEvent();
		}

		consensusTimestamp = in.readInstant();
		minGenInfo = Utilities.readList(in, LinkedList::new, stream -> {
			long key = stream.readLong();
			long value = stream.readLong();
			return Pair.of(key, value);
		});

		State.linkParents(events);

		if (version < ClassVersion.ADD_LAST_TRANS_TIME) {
			lastTransactionTimestamp = events[events.length - 1].getLastTransTime();
		} else {
			lastTransactionTimestamp = in.readInstant();
		}
	}

	public long getRound() {
		return round;
	}

	public long getNumEventsCons() {
		return numEventsCons;
	}

	public Hash getHashEventsCons() {
		return hashEventsCons;
	}

	public AddressBook getAddressBook() {
		return addressBook;
	}

	public EventImpl[] getEvents() {
		return events;
	}

	public Instant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public List<Pair<Long, Long>> getMinGenInfo() {
		return minGenInfo;
	}

	public void setRound(long round) {
		this.round = round;
	}

	public void setNumEventsCons(long numEventsCons) {
		this.numEventsCons = numEventsCons;
	}

	public void setHashEventsCons(Hash hashEventsCons) {
		this.hashEventsCons = hashEventsCons;
	}

	public void setAddressBook(AddressBook addressBook) {
		this.addressBook = addressBook;
	}

	public void setEvents(EventImpl[] events) {
		this.events = events;
	}

	public void setConsensusTimestamp(Instant consensusTimestamp) {
		this.consensusTimestamp = consensusTimestamp;
	}

	public void setMinGenInfo(List<Pair<Long, Long>> minGenInfo) {
		this.minGenInfo = minGenInfo;
	}

	public Instant getLastTransactionTimestamp() {
		return lastTransactionTimestamp;
	}

	public void setLastTransactionTimestamp(Instant lastTransactionTimestamp) {
		this.lastTransactionTimestamp = lastTransactionTimestamp;
	}

	/**
	 * Sets the LastTransactionTimestamp based on the events contained
	 */
	void setLastTransactionTimestampFromEvents() {
		if (events != null && events.length > 0) {
			setLastTransactionTimestamp(
					events[events.length - 1].getLastTransTime()
			);
		}
	}

	public void createSnapshot() {
		if (StateSettings.compareSSLeafSnapshots) {
			snapshot = SelfSerializableByteSnapshot.createSnapshot(this);
		}
	}

	public boolean compareSnapshot() {
		if (!StateSettings.compareSSLeafSnapshots || snapshot == null) {
			return false;
		}
		SelfSerializableByteSnapshot<PlatformState> current = SelfSerializableByteSnapshot.createSnapshot(this);
		if (!current.getHash().equals(snapshot.getHash())) {
			log.error(LogMarker.EXCEPTION.getMarker(),
					"PlatformState has been altered!\nCurrent state:\n{}\nPrevious state:\n{}",
					current.getBytesAsHexString(),
					snapshot.getBytesAsHexString());
			// Log the mismatch only once
			StateSettings.compareSSLeafSnapshots = false;
			return true;
		}
		return false;
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return ClassVersion.LOAD_MINGEN_INTO_CONSENSUS;
	}

	@Override
	public boolean isDataExternal() {
		return false;
	}

	@Override
	public PlatformState copy() {
		return new PlatformState(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		PlatformState that = (PlatformState) o;

		return new EqualsBuilder()
				.append(round, that.round)
				.append(numEventsCons, that.numEventsCons)
				.append(hashEventsCons, that.hashEventsCons)
				.append(addressBook, that.addressBook)
				.append(events, that.events)
				.append(consensusTimestamp, that.consensusTimestamp)
				.append(minGenInfo, that.minGenInfo)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(round)
				.append(numEventsCons)
				.append(hashEventsCons)
				.append(addressBook)
				.append(events)
				.append(consensusTimestamp)
				.append(minGenInfo)
				.toHashCode();
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("round", round)
				.append("numEventsCons", numEventsCons)
				.append("hashEventsCons", hashEventsCons)
				.append("addressBook", addressBook)
				.append("events", events)
				.append("consensusTimestamp", consensusTimestamp)
				.append("minGenInfo", minGenInfo)
				.toString();
	}
}

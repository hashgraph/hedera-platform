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
package com.swirlds.platform.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.swirlds.common.AddressBook;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.SwirldState;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.event.AbbreviatedStateEvent;
import com.swirlds.platform.internal.CreatorSeqPair;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static com.swirlds.logging.LogMarker.EXCEPTION;

/**
 * This is a signed state, in a form that allows those outside the network to verify that it is a legitimate
 * state at a given time.
 *
 * It includes a copy of a SwirldsState at a given moment, and the address book, and the history of the
 * address book, and a set of signatures (with identities of the signers) attesting to it.
 *
 * It can be created at the moment all the famous witnesses become known for a given round. The signatures
 * can be created and collected for every round, or for every Nth round, or for each round whose last famous
 * witness has a consensus time stamp at least T seconds after the last signed state, or by some other
 * criterion.
 *
 * The signed state is also saved to disk, and is given to a new member joining the network, or to an old
 * member rejoining after a long absence.
 */
public class SignedState extends AbstractMerkleInternal implements FastCopyable<SignedState>, SignedStateInfo,
		MerkleInternal {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();
	/** the maximum number of events allowed when de-serializing a state */
	static final int MAX_EVENTS_IN_STATE = 1000000;

	private static final long CLASS_ID = 0x2971b4ba7dd84402L;

	private static class ClassVersion {
		public static final int ORIGINAL = 1;
		public static final int ADD_MIN_GEN = 2;
		public static final int EVENT_REFACTOR = 3;
		public static final int MIGRATE_TO_SERIALIZABLE = 4;
	}

	/** the signatures collected so far (including from self) */
	private SigSet sigSet;

	// /** the hash of the SwirldsState of SwirldState2 object being saved */
	// private byte[] swirldStateHash = null;
	// /** the hash of the AddressBook being saved */
	// private byte[] addressBookHash = null;

	/** specifies whether this state should be saved to disk */
	private boolean shouldSaveToDisk;

	/** Is this the last state saved before the freeze period */
	private boolean freezeState;

	/** Indicates whether the state has been archived or not */
	private boolean archived = false;

	/** Indicates whether the state has been staged for archival yet */
	private boolean markedForArchiving = false;

	/**
	 * Counts the number of reservations for use.
	 * When greater than 0 this state will not be archived or deleted.
	 */
	private int reservations = 0;

	/**
	 * Counts the number of reservations that can tolerate the state being archived.
	 * When greater than 0 this state will not be deleted.
	 */
	private int weakReservations = 0;

	/**
	 * Indicates whether this {@link SignedState} has been written to disk
	 */
	private boolean savedToDisk = false;

	/** Data that is stored to local storage and is not hashed */
	private LocalStateEvents localStateEvents;

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public SigSet getSigSet() {
		return sigSet;
	}

	private StateSettings stateSettings;

	private static class ChildIndices {
		/**
		 * A fast copy of the state resulting from all transactions in consensus order from all events with
		 * received rounds up through the round this SignedState represents.
		 */
		public static final int STATE = 0;
		/**
		 * Contains all other signed state data. In the future, this may be broken up into multiple smaller leaves.
		 */
		public static final int SIGNED_STATE_LEAF = 1;

		public static final int CHILD_COUNT = 2;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumberOfChildren() {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMaximumChildCount(int version) {
		return ChildIndices.CHILD_COUNT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean childHasExpectedType(int index, long childClassId, int version) {
		switch (index) {
			case ChildIndices.STATE:
				return true;
			case ChildIndices.SIGNED_STATE_LEAF:
				return childClassId == SignedStateLeaf.CLASS_ID;
			default:
				throw new IllegalChildIndexException(getMinimumChildCount(getVersion()),
						getMaximumChildCount(getVersion()), index);
		}
	}

	@JsonIgnore
	public SwirldState getState() {
		return getChild(ChildIndices.STATE);
	}

	public void setState(SwirldState state) {
		setChild(ChildIndices.STATE, state);
	}

	public SignedStateLeaf getLeaf() {
		return getChild(ChildIndices.SIGNED_STATE_LEAF);
	}

	private void setLeaf(SignedStateLeaf leaf) {
		setChild(ChildIndices.SIGNED_STATE_LEAF, leaf);
	}

	/**
	 * Instantiate a signed state, storing the information passed as parameters in it. Also calculate and
	 * store its hash, which can then be retrieved with getHash().
	 *
	 * @param state
	 * 		a fast copy of the state resulting from all transactions in consensus order from all
	 * 		events with received rounds up through the round this SignedState represents
	 * @param lastRoundReceived
	 * 		the last round number for which all the famous witnesses are known (i.e., for which the
	 * 		fame of every witness has been decided).
	 * @param numEventsCons
	 * 		how many consensus events have there been throughout all of history, up through the
	 * 		round
	 * 		received that this SignedState represents.
	 * @param hashEventsCons
	 * 		a running hash of all consensus events in history, up through the round received that
	 * 		this
	 * 		SignedState represents
	 * @param addressBook
	 * 		the addressBook, which has all members with their stake, for which those with more than 2/3 of total stake
	 * 		will need to sign (this must be immutable)
	 * @param events
	 * 		events for the round that is being signed and the preceding rounds, the events should be
	 * 		sorted from oldest to newest
	 * @param consensusTimestamp
	 * 		the consensus timestamp for this signed state
	 * @param freezeState
	 * 		specifies whether this state is the last one saved before the freeze
	 * @param minGenInfo
	 * 		the minimum generation of famous witnesses per round
	 */
	public SignedState(SwirldState state, long lastRoundReceived, long numEventsCons,
			Hash hashEventsCons, AddressBook addressBook, EventImpl[] events,
			Instant consensusTimestamp, boolean freezeState,
			List<Pair<Long, Long>> minGenInfo) {
		super();
		setState(state);
		setLeaf(new SignedStateLeaf());
		getLeaf().setRound(lastRoundReceived);
		getLeaf().setNumEventsCons(numEventsCons);
		getLeaf().setHashEventsCons(hashEventsCons);
		getLeaf().setAddressBook(addressBook);
		getLeaf().setEvents(events);
		getLeaf().setConsensusTimestamp(consensusTimestamp);
		this.freezeState = freezeState;
		getLeaf().setMinGenInfo(minGenInfo);
		getLeaf().setLastTransactionTimestampFromEvents();

		sigSet = new SigSet(addressBook);

		// create a snapshot of the leaf to compare it later and see if its modified
		getLeaf().createSnapshot();
	}

	private SignedState(final SignedState sourceState) {
		super();
		setState(sourceState.getState());
		setLeaf(new SignedStateLeaf());
		getLeaf().setRound(sourceState.getLastRoundReceived());
		getLeaf().setNumEventsCons(sourceState.getNumEventsCons());
		getLeaf().setHashEventsCons(sourceState.getHashEventsCons());
		getLeaf().setAddressBook(sourceState.getAddressBook().copy());
		getLeaf().setEvents(sourceState.getEvents());
		getLeaf().setConsensusTimestamp(sourceState.getConsensusTimestamp());
		getLeaf().setMinGenInfo(sourceState.getMinGenInfo());
		this.freezeState = sourceState.freezeState;
		this.sigSet = sourceState.getSigSet().copy();
	}

	/**
	 * Zero-arg constructor for signed state.
	 */
	public SignedState() {
		super();
	}

	/**
	 * This constructor is used for loading the SignedState from a byte stream. It needs an initial
	 * SwirldState to call its copyFrom method to load that state.
	 *
	 * @param state
	 * 		a blank state of the class that is stored in the byte stream
	 */
	public SignedState(SwirldState state) {
		super();
		setState(state);
		setLeaf(new SignedStateLeaf());
	}

	/**
	 * Get the hash of the signed state. This was actually calculated in the constructor.
	 *
	 * @return the hash
	 */
	@Deprecated
	public byte[] getHashBytes() {
		return getHash().getValue();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLastRoundReceived() {
		return getLeaf().getRound();
	}

	/**
	 * Get the events for the round that is being signed and the preceding rounds
	 *
	 * @return Event array, can be null
	 */
	public EventImpl[] getEvents() {
		return getLeaf().getEvents();
	}

	/**
	 * Get the number of consensus events processed by the state that is being signed
	 *
	 * @return the number of consensus events processed
	 */
	public long getNumEventsCons() {
		return getLeaf().getNumEventsCons();
	}

	/**
	 * Get the running hash of all events processed by the state that is being signed
	 *
	 * @return the running hash of all events processed
	 */
	public Hash getHashEventsCons() {
		return getLeaf().getHashEventsCons();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AddressBook getAddressBook() {
		return getLeaf().getAddressBook();
	}

	public Instant getConsensusTimestamp() {
		return getLeaf().getConsensusTimestamp();
	}

	public void setAddressBook(AddressBook addressBook) {
		getLeaf().setAddressBook(addressBook);
	}

	boolean shouldSaveToDisk() {
		return shouldSaveToDisk;
	}

	public void setShouldSaveToDisk(boolean shouldSaveToDisk) {
		this.shouldSaveToDisk = shouldSaveToDisk;
	}

	public boolean isFreezeState() {
		return freezeState;
	}

	byte[] getSwirldStateHash() {
		return getState().getHash().getValue();
	}

	public Hash getStateHash() {
		return getState().getHash();
	}

	public List<Pair<Long, Long>> getMinGenInfo() {
		return getLeaf().getMinGenInfo();
	}

	public Instant getLastTransactionTimestamp() {
		return getLeaf().getLastTransactionTimestamp();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SignedState copy() {
		throwIfImmutable();
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFrom(SerializableDataInputStream inStream) throws IOException {
		Cryptography crypto = CryptoFactory.getInstance();

		long instanceVersion = inStream.readLong();// classVersion
		byte[] hashBytes = Utilities.readByteArray(inStream);
		setHash(new Hash(hashBytes));
		getState().copyFrom(inStream);
		getLeaf().setRound(inStream.readLong());
		getLeaf().setNumEventsCons(inStream.readLong());
		getLeaf().setAddressBook(new AddressBook());
		getLeaf().getAddressBook().copyFrom(inStream);
		crypto.digestSync(getLeaf().getAddressBook());
		getLeaf().setHashEventsCons(new Hash(Utilities.readByteArray(inStream)));
		getLeaf().setConsensusTimestamp(Utilities.readInstant(inStream));
		shouldSaveToDisk = inStream.readBoolean();

		// before version 3, we used to store events differently
		if (instanceVersion < ClassVersion.EVENT_REFACTOR) {
			HashMap<CreatorSeqPair, EventImpl> eventsByCreatorSeq = new HashMap<>();

			int numEvents = inStream.readInt();
			getLeaf().setEvents(new EventImpl[numEvents]);
			for (int i = 0; i < getLeaf().getEvents().length; i++) {
				getLeaf().getEvents()[i] = AbbreviatedStateEvent.readAbbreviatedConsensusEvent(inStream,
						eventsByCreatorSeq);
				eventsByCreatorSeq.put(getLeaf().getEvents()[i].getCreatorSeqPair(), getLeaf().getEvents()[i]);
			}
		} else {
			getLeaf().setEvents(inStream.readSerializableList(
					MAX_EVENTS_IN_STATE,
					false,
					EventImpl::new).toArray(new EventImpl[0]));
			hashEvents(getLeaf().getEvents());
			linkParents(getLeaf().getEvents());
		}
		// We do not have lastTransactionTimestamp written in this version, so we base it on the timestamp of the
		// signed state. This is not an issue since we will not use copyFrom to do a reconnect, only on a restart
		getLeaf().setLastTransactionTimestamp(getLeaf().getConsensusTimestamp());

		getLeaf().setMinGenInfo(Utilities.readList(inStream, LinkedList::new, (stream) -> {
			long key = stream.readLong();
			long value = stream.readLong();
			return Pair.of(key, value);
		}));

		sigSet = new SigSet(getLeaf().getAddressBook());
		sigSet.copyFrom(inStream);
	}

	static void hashEvents(EventImpl[] events) {
		Cryptography crypto = CryptoFactory.getInstance();
		for (EventImpl event : events) {
			crypto.digestSync(event.getBaseEventHashedData());
		}
	}

	public static void linkParents(EventImpl[] events) {
		HashMap<CreatorSeqPair, EventImpl> eventsByCreatorSeq = new HashMap<>();
		for (EventImpl event : events) {
			eventsByCreatorSeq.put(event.getCreatorSeqPair(), event);
			event.setSelfParent(
					eventsByCreatorSeq.get(
							new CreatorSeqPair(
									event.getCreatorId(),
									event.getCreatorSeq() - 1
							))
			);
			event.setOtherParent(
					eventsByCreatorSeq.get(
							new CreatorSeqPair(
									event.getOtherId(),
									event.getOtherSeq()
							))
			);
		}
	}

	/**
	 * Get whether this signed state has been written to disk.
	 *
	 * @return true if this {@link SignedState} has been written to disk, false otherwise
	 */
	@JsonIgnore
	synchronized boolean isSavedToDisk() {
		return savedToDisk;
	}

	/**
	 * Sets whether this signed state has been written to disk.
	 *
	 * @param savedToDisk
	 * 		indicates whether or not this {@link SignedState} has been written to disk
	 */
	public synchronized void setSavedToDisk(final boolean savedToDisk) {
		this.savedToDisk = savedToDisk;
	}

	/**
	 * Delete the SignedState if the it is not reserved for use. Always call this method when deleting a signed state.
	 * It is never safe to directly delete a signed state via {@link SignedState#release}.
	 *
	 * @return true if deleted, false otherwise
	 */
	public synchronized boolean tryDelete() {
		if (isReleased()) {
			log.warn(EXCEPTION.getMarker(), "State for round '{}' already deleted!", this::getLastRoundReceived);
			return true;
		}
		if (reservations == 0 && weakReservations == 0) {
			try {
				release();
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception while deleting saved state:", e);
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Method used to guarantee that a SignedState is not put in the archival queue more than once.
	 *
	 * @return false the first time called, true afterwards.
	 */
	@JsonIgnore
	public boolean isMarkedForArchiving() {
		boolean ret = markedForArchiving;
		markedForArchiving = true;
		return ret;
	}

	/**
	 * Delete the SignedState if the it is not reserved for use. This method should implement any cleanup that won't
	 * be done automatically by the Garbage Collector,like calling the delete() method on any saved SwirldState object
	 *
	 * @return true if deleted, false otherwise
	 */
	public synchronized boolean tryArchive() {
		if (isReleased()) {
			archived = true;
			return true;
		}
		if (archived) {
			log.warn(EXCEPTION.getMarker(), "State already archived!");
			return true;
		}
		if (reservations == 0) {
			try {
				getState().archive();
			} catch (Exception e) {
				log.error(EXCEPTION.getMarker(),
						"Exception while archiving saved state:", e);
			}
			archived = true;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Reserves the SignedState for use. While reserved, this SignedState cannot be
	 * deleted or archived, so it is very important to call releaseState() on it when done.
	 */
	public synchronized void reserveState() {
		if (isReleased()) {
			throw new RuntimeException("State can not be reserved after it has been deleted.");
		}
		reservations++;
	}

	/**
	 * Releases a reservation previously obtained in tryReserve()
	 */
	public synchronized void releaseState() {
		if (reservations == 0) {
			throw new RuntimeException("releaseState() called too many times.");
		}
		reservations--;
	}

	/**
	 * Reserves a state for use. While reserved this state may not be deleted but it may be archived.
	 * It is very important to call weakReleaseState() on it when done.
	 */
	public synchronized void weakReserveState() {
		if (isReleased()) {
			throw new RuntimeException("State can not be reserved after it has been deleted.");
		}
		weakReservations++;
	}

	/**
	 * Release an archival reservation.
	 */
	public synchronized void weakReleaseState() {
		if (weakReservations == 0) {
			throw new RuntimeException("releaseArchival() called too many times.");
		}
		weakReservations--;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void copyFromExtra(SerializableDataInputStream inStream) throws IOException {
		getState().copyFromExtra(inStream);
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
		return ClassVersion.MIGRATE_TO_SERIALIZABLE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getMinimumSupportedVersion() {
		return ClassVersion.ADD_MIN_GEN;
	}

	public LocalStateEvents getLocalStateEvents() {
		return localStateEvents;
	}

	public void setLocalStateEvents(LocalStateEvents localStateEvents) {
		this.localStateEvents = localStateEvents;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) return false;

		SignedState that = (SignedState) o;

		return new EqualsBuilder()
				.append(sigSet, that.sigSet)
				.append(getLeaf(), that.getLeaf())
				.append(getState(), that.getState())
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(sigSet)
				.append(getLeaf())
				.append(getState())
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("sigSet", sigSet)
				.append("leaf", getLeaf())
				.append("state", getState())
				.toString();
	}

	/**
	 * Attach signatures to this state.
	 */
	public void setSigSet(SigSet sigSet) {
		this.sigSet = sigSet;
	}
}

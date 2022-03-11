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
import com.swirlds.common.Releasable;
import com.swirlds.common.SwirldState;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.EventImpl;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
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
public class SignedState implements Releasable, SignedStateInfo {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger();

	/** the signatures collected so far (including from self) */
	private SigSet sigSet;

	/** specifies whether this state should be saved to disk */
	private boolean shouldSaveToDisk;

	/** Is this the last state saved before the freeze period */
	private boolean freezeState;

	/** Indicates whether the state has been archived or not */
	private boolean archived = false;

	/** Indicates whether the state has been staged for archival yet */
	private boolean markedForArchival = false;

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
	 * True if the signed state has been deleted.
	 */
	private boolean released;

	/**
	 * Indicates whether this {@link SignedState} has been written to disk
	 */
	private boolean savedToDisk = false;

	/**
	 * True if this state has gathered sufficient signatures, otherwise false.
	 */
	private boolean complete;

	/** Data that is stored to local storage and is not hashed */
	private LocalStateEvents localStateEvents;

	private State state;

	/**
	 * A cached copy of the address book.
	 */
	private AddressBook addressBook;

	@Override
	public long getLastRoundReceived() {
		return state.getPlatformState().getRound();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SigSet getSigSet() {
		return sigSet;
	}

	@Override
	public AddressBook getAddressBook() {
		if (addressBook == null) {
			addressBook = getSwirldState().getAddressBookCopy();
		}
		return addressBook;
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
	public SignedState(
			final State state,
			final long lastRoundReceived,
			final long numEventsCons,
			final Hash hashEventsCons,
			final AddressBook addressBook,
			final EventImpl[] events,
			final Instant consensusTimestamp,
			final boolean freezeState,
			final List<Pair<Long, Long>> minGenInfo) {

		this.state = state;
		state.incrementReferenceCount();

		state.setPlatformState(new PlatformState());
		state.getPlatformState().setRound(lastRoundReceived);
		state.getPlatformState().setNumEventsCons(numEventsCons);
		state.getPlatformState().setHashEventsCons(hashEventsCons);
		state.getPlatformState().setAddressBook(addressBook);
		state.getPlatformState().setEvents(events);
		state.getPlatformState().setConsensusTimestamp(consensusTimestamp);
		state.getPlatformState().setMinGenInfo(minGenInfo);
		state.getPlatformState().setLastTransactionTimestampFromEvents();

		this.freezeState = freezeState;
		sigSet = new SigSet(addressBook);
	}

	/**
	 * Zero-arg constructor for signed state.
	 */
	public SignedState() {
		super();
	}

	public SignedState(State state) {
		state.incrementReferenceCount();
		this.state = state;
	}

	/**
	 * Get the root of the state. This object should not be held beyond the scope of this SignedState
	 * or else there is risk that the state may be deleted/archived unexpectedly.
	 *
	 * @return the state contained in the signed state
	 */
	public State getState() {
		return state;
	}

	/**
	 * @return whether this signed state should be saved to disk
	 */
	boolean shouldSaveToDisk() {
		return shouldSaveToDisk;
	}

	/**
	 * @param shouldSaveToDisk
	 * 		whether this signed state should be saved to disk
	 */
	public void setShouldSaveToDisk(boolean shouldSaveToDisk) {
		this.shouldSaveToDisk = shouldSaveToDisk;
	}

	/**
	 * @return is this the last state saved before the freeze period
	 */
	public boolean isFreezeState() {
		return freezeState;
	}

	/**
	 * Get whether this signed state has been written to disk.
	 *
	 * @return true if this {@link SignedState} has been written to disk, false otherwise
	 */
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
	 * Check if this state has been marked for archival.
	 */
	public boolean isMarkedForArchival() {
		return markedForArchival;
	}

	/**
	 * Mark this state as having been added to the archival queue.
	 */
	public void markForArchival() {
		markedForArchival = true;
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
				state.getSwirldState().archive();
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
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		SignedState that = (SignedState) o;

		return new EqualsBuilder()
				.append(sigSet, that.sigSet)
				.append(state, that.state)
				.isEquals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(sigSet)
				.append(state)
				.toHashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
				.append("sigSet", sigSet)
				.append("state", state)
				.toString();
	}

	/**
	 * Attach signatures to this state.
	 *
	 * @param sigSet
	 * 		the signatures to be attached to this signed state
	 */
	public void setSigSet(SigSet sigSet) {
		this.sigSet = sigSet;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isReleased() {
		return released;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
		released = true;
		state.decrementReferenceCount();
	}

	/**
	 * Get the consensus timestamp for this signed state
	 *
	 * @return the consensus timestamp for this signed state.
	 */
	public Instant getConsensusTimestamp() {
		return state.getPlatformState().getConsensusTimestamp();
	}

	/**
	 * Get the root node of the application's state
	 *
	 * @return the root node of the application's state.
	 */
	public SwirldState getSwirldState() {
		return state.getSwirldState();
	}

	/**
	 * Get the hash of the state's merkle tree
	 *
	 * @return the hash of the state's merkle tree.
	 */
	public Hash getStateHash() {
		return state.getHash();
	}

	/**
	 * Get the hash of the state.
	 *
	 * @return the hash
	 * @deprecated we should not be directly touching the bytes of a hash
	 */
	@Deprecated
	public byte[] getStateHashBytes() {
		return state.getHash().getValue();
	}

	/**
	 * Get the hash of the swirld state.
	 *
	 * @return the hash
	 * @deprecated we should not be directly touching the bytes of a hash
	 */
	@Deprecated
	public byte[] getSwirldStateHashBytes() {
		return state.getSwirldState().getHash().getValue();
	}

	/**
	 * Get events in the platformState.
	 *
	 * @return events in the platformState
	 */
	public EventImpl[] getEvents() {
		return state.getPlatformState().getEvents();
	}

	/**
	 * Get the hash of the consensus events in this state.
	 *
	 * @return the hash of the consensus events in this state
	 */
	public Hash getHashEventsCons() {
		return state.getPlatformState().getHashEventsCons();
	}

	/**
	 * Get the number of consensus events in this state.
	 *
	 * @return the number of consensus events in this state
	 */
	public long getNumEventsCons() {
		return state.getPlatformState().getNumEventsCons();
	}

	/**
	 * Get information about the minimum generation in this round.
	 *
	 * @return the minimum generation of famous witnesses per round
	 */
	public List<Pair<Long, Long>> getMinGenInfo() {
		return state.getPlatformState().getMinGenInfo();
	}

	/**
	 * Get the timestamp of the last transaction added to this state.
	 *
	 * @return the timestamp of the last transaction added to this state
	 */
	public Instant getLastTransactionTimestamp() {
		return state.getPlatformState().getLastTransactionTimestamp();
	}

	/**
	 * Return true if this state has gathered enough signatures.
	 */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * Specify if this state has gathered enough signatures.
	 */
	public void markAsComplete() {
		this.complete = true;
	}
}

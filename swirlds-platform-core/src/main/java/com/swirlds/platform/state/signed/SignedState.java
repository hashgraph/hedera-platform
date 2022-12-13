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
package com.swirlds.platform.state.signed;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.ReferenceCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a signed state, in a form that allows those outside the network to verify that it is a
 * legitimate state at a given time.
 *
 * <p>It includes a copy of a SwirldsState at a given moment, and the address book, and the history
 * of the address book, and a set of signatures (with identities of the signers) attesting to it.
 *
 * <p>It can be created at the moment all the famous witnesses become known for a given round. The
 * signatures can be created and collected for every round, or for every Nth round, or for each
 * round whose last famous witness has a consensus time stamp at least T seconds after the last
 * signed state, or by some other criterion.
 *
 * <p>The signed state is also saved to disk, and is given to a new member joining the network, or
 * to an old member rejoining after a long absence.
 */
public class SignedState implements SignedStateInfo {

    private static final Logger LOG = LogManager.getLogger();

    /** the signatures collected so far (including from self) */
    private SigSet sigSet;

    /** Is this the last state saved before the freeze period */
    private boolean freezeState;

    /**
     * A reference count that prevents the state from being archived or deleted. Also known as a
     * "strong reservation".
     */
    private final ReferenceCounter doNotArchiveOrDelete = new ReferenceCounter(this::archive);

    /**
     * A reference count that prevents the state from being deleted, but does not prevent the state
     * from being archived. Also known as a "weak reservation".
     */
    private final ReferenceCounter doNotDelete =
            new ReferenceCounter(this::archiveOrDeleteInBackground);

    /**
     * True if this state has been deleted. Used to prevent the same state from being deleted more
     * than once.
     */
    private boolean deleted = false;

    /** The root of the merkle state. */
    private State state;

    /** A cached copy of the address book. */
    private AddressBook addressBook;

    /** The timestamp of when this object was created. */
    private final Instant creationTimestamp = Instant.now();

    /** If true, then this state should eventually be written to disk. */
    private boolean stateToSave;

    /** Signed states are deleted on this background thread. */
    private SignedStateGarbageCollector signedStateGarbageCollector;

    /**
     * Instantiate a signed state, storing the information passed as parameters in it. Also
     * calculate and store its hash, which can then be retrieved with getHash().
     *
     * @param state a fast copy of the state resulting from all transactions in consensus order from
     *     all events with received rounds up through the round this SignedState represents
     * @param round the round of this signed state
     * @param numEventsCons how many consensus events have there been throughout all of history, up
     *     through the round received that this SignedState represents.
     * @param hashEventsCons a running hash of all consensus events in history, up through the round
     *     received that this SignedState represents
     * @param addressBook the addressBook, which has all members with their stake, for which those
     *     with more than 2/3 of total stake will need to sign (this must be immutable)
     * @param events events for the round that is being signed and the preceding rounds, the events
     *     should be sorted from oldest to newest
     * @param consensusTimestamp the consensus timestamp for this signed state
     * @param freezeState specifies whether this state is the last one saved before the freeze
     * @param minGenInfo the minimum generation of famous witnesses per round
     */
    public SignedState(
            final State state,
            final long round,
            final long numEventsCons,
            final Hash hashEventsCons,
            final AddressBook addressBook,
            final EventImpl[] events,
            final Instant consensusTimestamp,
            final boolean freezeState,
            final List<MinGenInfo> minGenInfo,
            final SoftwareVersion softwareVersion) {

        this(state);

        final PlatformState platformState = new PlatformState();
        final PlatformData platformData = new PlatformData();

        state.setPlatformState(platformState);
        platformState.setPlatformData(platformData);

        platformData.setRound(round);
        platformData.setNumEventsCons(numEventsCons);
        platformData.setHashEventsCons(hashEventsCons);
        platformData.setEvents(events);
        platformData.setConsensusTimestamp(consensusTimestamp);
        platformData.setMinGenInfo(minGenInfo);
        platformData.calculateLastTransactionTimestampFromEvents();
        platformData.setCreationSoftwareVersion(softwareVersion);

        platformState.setAddressBook(addressBook);

        this.freezeState = freezeState;
        sigSet = new SigSet(addressBook);
    }

    public SignedState(final State state) {
        state.reserve();

        // This reservation is released when doNotArchiveOrDelete fully counts down
        doNotDelete.reserve();

        this.state = state;
    }

    /** Set a garbage collector, used to delete states on a background thread. */
    public synchronized void setGarbageCollector(
            final SignedStateGarbageCollector signedStateGarbageCollector) {
        this.signedStateGarbageCollector = signedStateGarbageCollector;
    }

    /** {@inheritDoc} */
    @Override
    public long getRound() {
        return state.getPlatformState().getPlatformData().getRound();
    }

    /** {@inheritDoc} */
    @Override
    public SigSet getSigSet() {
        return sigSet;
    }

    /**
     * Attach signatures to this state.
     *
     * @param sigSet the signatures to be attached to this signed state
     */
    public void setSigSet(final SigSet sigSet) {
        this.sigSet = sigSet;
    }

    @Override
    public AddressBook getAddressBook() {
        if (addressBook == null) {
            addressBook = getSwirldState().getAddressBookCopy();
        }
        return addressBook;
    }

    /**
     * Get the root of the state. This object should not be held beyond the scope of this
     * SignedState or else there is risk that the state may be deleted/archived unexpectedly.
     *
     * @return the state contained in the signed state
     */
    public State getState() {
        return state;
    }

    /**
     * @return is this the last state saved before the freeze period
     */
    public boolean isFreezeState() {
        return freezeState;
    }

    /**
     * Reserves the SignedState for use. While reserved, this SignedState cannot be deleted or
     * archived, so it is very important to call releaseState() on it when done.
     */
    public void reserveState() {
        doNotArchiveOrDelete.reserve();
    }

    /**
     * Reserves a state for use. While reserved this state may not be deleted but it may be
     * archived. It is very important to call weakReleaseState() on it when done.
     */
    public void weakReserveState() {
        doNotDelete.reserve();
    }

    /** Releases a reservation previously obtained in reserveState() */
    public void releaseState() {
        doNotArchiveOrDelete.release();
    }

    /** Release an archival reservation. */
    public void weakReleaseState() {
        doNotDelete.release();
    }

    /** Add this state to the queue to be deleted/archived on a background thread. */
    private void archiveOrDeleteInBackground() {
        if (signedStateGarbageCollector == null
                || !signedStateGarbageCollector.executeOnGarbageCollectionThread(
                        this::archiveOrDelete)) {
            LOG.warn(
                    SIGNED_STATE.getMarker(),
                    "unable to enqueue state for deletion/archival, "
                            + "will delete/archive state on calling thread {}",
                    Thread.currentThread().getName());
            synchronized (this) {
                archiveOrDelete();
            }
        }
    }

    /** Called when the {@link #doNotArchiveOrDelete} counter is fully released. */
    private void archive() {
        // Release the reference count taken in the constructor. Deletion is still prevented
        // if there are weak reservations being held.
        final boolean willBeDeleted = doNotDelete.release();

        if (!willBeDeleted) {
            // Only send a task to the background handle thread if this object is not yet ready to
            // be deleted.
            // If it is time to be deleted, the delete method will enqueue the task, and we don't
            // need to
            // duplicate the effort.
            archiveOrDeleteInBackground();
        }
    }

    /**
     * Perform archival/deletion on this signed state.
     *
     * <p>Under normal operation, this method will only be called on the single-threaded background
     * archive/deletion handler. However, if the queue fills up then a different thread may attempt
     * to simultaneously call this method. Because of that, this method must be synchronized.
     */
    private synchronized void archiveOrDelete() {
        final Instant start = Instant.now();

        if (doNotDelete.isDestroyed()) {
            if (!deleted) {
                try {
                    deleted = true;

                    state.release();

                    if (signedStateGarbageCollector != null) {
                        signedStateGarbageCollector.reportDeleteTime(
                                Duration.between(start, Instant.now()));
                    }
                } catch (final Throwable ex) {
                    LOG.error(
                            EXCEPTION.getMarker(),
                            "exception while attempting to delete signed state",
                            ex);
                }
            }
        } else {
            final SwirldState swirldState = state.getSwirldState();
            if (swirldState != null) {
                try {
                    swirldState.archive();

                    if (signedStateGarbageCollector != null) {
                        signedStateGarbageCollector.reportArchiveTime(
                                Duration.between(start, Instant.now()));
                    }
                } catch (final Throwable ex) {
                    LOG.error(
                            EXCEPTION.getMarker(),
                            "exception while attempting to archive signed state",
                            ex);
                }
            }
        }
    }

    /** Get the number of strong reservations. */
    public synchronized int getReservations() {
        return doNotArchiveOrDelete.getReservationCount();
    }

    /** Get the number of weak reservations. */
    public synchronized int getWeakReservations() {
        return doNotDelete.getReservationCount();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SignedState that = (SignedState) o;

        return new EqualsBuilder().append(sigSet, that.sigSet).append(state, that.state).isEquals();
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(sigSet).append(state).toHashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format(
                "SS(round: %d, sigs: %d/%s, hash: %s)",
                getRound(),
                (sigSet == null ? 0 : sigSet.getStakeCollected()),
                (addressBook == null ? "?" : addressBook.getTotalStake()),
                state.getHash());
    }

    /**
     * Get the consensus timestamp for this signed state
     *
     * @return the consensus timestamp for this signed state.
     */
    public Instant getConsensusTimestamp() {
        return state.getPlatformState().getPlatformData().getConsensusTimestamp();
    }

    /** The wall clock time when this SignedState object was instantiated. */
    public Instant getCreationTimestamp() {
        return creationTimestamp;
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
     * Get events in the platformState.
     *
     * @return events in the platformState
     */
    public EventImpl[] getEvents() {
        return state.getPlatformState().getPlatformData().getEvents();
    }

    /**
     * Get the hash of the consensus events in this state.
     *
     * @return the hash of the consensus events in this state
     */
    public Hash getHashEventsCons() {
        return state.getPlatformState().getPlatformData().getHashEventsCons();
    }

    /**
     * Get the number of consensus events in this state.
     *
     * @return the number of consensus events in this state
     */
    public long getNumEventsCons() {
        return state.getPlatformState().getPlatformData().getNumEventsCons();
    }

    /**
     * Get information about the minimum generation in this round.
     *
     * @return the minimum generation of famous witnesses per round
     */
    public List<MinGenInfo> getMinGenInfo() {
        return state.getPlatformState().getPlatformData().getMinGenInfo();
    }

    /**
     * The minimum generation of famous witnesses for the round specified. This method only looks at
     * non-ancient rounds contained within this state.
     *
     * @param round the round whose minimum generation will be returned
     * @return the minimum generation for the round specified
     * @throws NoSuchElementException if the generation information for this round is not contained
     *     withing this state
     */
    public long getMinGen(final long round) {
        for (final MinGenInfo minGenInfo : getMinGenInfo()) {
            if (minGenInfo.round() == round) {
                return minGenInfo.minimumGeneration();
            }
        }
        throw new NoSuchElementException("No minimum generation found for round: " + round);
    }

    /**
     * Return the round generation of the oldest round in this state
     *
     * @return the generation of the oldest round
     */
    public long getMinRoundGeneration() {
        return getMinGenInfo().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MinGen info found in state"))
                .minimumGeneration();
    }

    /**
     * Get the timestamp of the last transaction added to this state.
     *
     * @return the timestamp of the last transaction added to this state
     */
    public Instant getLastTransactionTimestamp() {
        return state.getPlatformState().getPlatformData().getLastTransactionTimestamp();
    }

    /**
     * Check that the size of the signature set matches the number of nodes in the provided address
     * book. If it has fewer entries, create a new {@link SigSet} of the correct size and copy the
     * existing signatures into it. This can be necessary when a new node reconnects from genesis
     * and is provided a signed state created by the network prior to this node joining.
     *
     * @param newAddressBook the address book to check if the sigset needs to be expanded
     */
    public void expandSigSetIfNeeded(final AddressBook newAddressBook) {
        if (sigSet.getNumMembers() < newAddressBook.getSize()) {
            final SigSet newSigSet = new SigSet(newAddressBook);
            for (int i = 0; i < sigSet.getNumMembers(); i++) {
                final SigInfo sigInfo = sigSet.getSigInfo(i);
                if (sigInfo != null) {
                    newSigSet.addSigInfo(sigInfo);
                }
            }
            setSigSet(newSigSet);
        }
    }

    /**
     * Check if this is a state that needs to be eventually written to disk.
     *
     * @return true if this state eventually needs to be written to disk
     */
    public boolean isStateToSave() {
        return stateToSave;
    }

    /**
     * Set if this state eventually needs to be written to disk.
     *
     * @param stateToSave true if this state eventually needs to be written to disk
     */
    public void setStateToSave(final boolean stateToSave) {
        this.stateToSave = stateToSave;
    }
}

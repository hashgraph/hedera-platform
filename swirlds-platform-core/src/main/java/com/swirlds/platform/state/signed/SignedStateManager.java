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
import static com.swirlds.platform.state.signed.SourceOfSignedState.TRANSACTIONS;
import static com.swirlds.platform.system.Fatal.fatalError;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.notification.NotificationFactory;
import com.swirlds.common.sequence.set.SequenceSet;
import com.swirlds.common.sequence.set.StandardSequenceSet;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.Startable;
import com.swirlds.platform.Settings;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.dispatch.triggers.transaction.PreConsensusStateSignatureTrigger;
import com.swirlds.platform.reconnect.emergency.EmergencyStateFinder;
import com.swirlds.platform.state.notifications.NewSignedStateBeingTrackedListener;
import com.swirlds.platform.state.notifications.NewSignedStateBeingTrackedNotification;
import com.swirlds.platform.state.notifications.StateHasEnoughSignaturesListener;
import com.swirlds.platform.state.notifications.StateHasEnoughSignaturesNotification;
import com.swirlds.platform.state.notifications.StateLacksSignaturesListener;
import com.swirlds.platform.state.notifications.StateLacksSignaturesNotification;
import com.swirlds.platform.state.notifications.StateSelfSignedListener;
import com.swirlds.platform.state.notifications.StateSelfSignedNotification;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Data structures and methods to manage the lifecycle of signed states. This class ensures that the
 * following states are always in memory:
 *
 * <ul>
 *   <li>The most recent fully-signed state
 *   <li>All the non-ancient states that are not fully signed
 *   <li>Any state that is currently in the process of being written to disk (no matter how old it
 *       is)
 *   <li>Any state that is being used for a reconnect
 *   <li>Any state that the application has taken a reservation on
 * </ul>
 */
public class SignedStateManager implements Startable, EmergencyStateFinder {

    private static final Logger LOG = LogManager.getLogger(SignedStateManager.class);

    /** The latest signed state signed by members with more than 1/3 of total stake. */
    private final SignedStateReference lastCompleteSignedState = new SignedStateReference();

    /** The latest signed state. May be unhashed. May or may not have all of its signatures. */
    private final SignedStateReference lastState = new SignedStateReference();

    /**
     * Signed states awaiting signatures. These states are fresh. That is, there exist no states
     * from later rounds that have collected enough signatures to be complete.
     */
    private final SignedStateMap freshUnsignedStates = new SignedStateMap(true);

    /**
     * Signed states awaiting signatures. These states are stale. That is, there are states from
     * later rounds that have collected enough signatures to be complete. We keep these states
     * around for a while since we may want to write them to disk or use them to detect ISS events.
     */
    private final SignedStateMap staleUnsignedStates = new SignedStateMap(false);

    private final Settings settings = Settings.getInstance();

    /** A signature that was received when there was no state with a matching round. */
    private record SavedSignature(long round, long memberId, Signature signature) {}

    /** Signatures for rounds in the future. */
    private final SequenceSet<SavedSignature> savedSignatures;

    /** The address book for this network. */
    private final AddressBook addressBook;

    /** the member ID for self */
    private final NodeId selfId;

    /** An object responsible for signing states with this node's key. */
    private final HashSigner signer;

    /** Called when a state enters the pipeline during a recovery. */
    private final Consumer<SignedState> recoveryHandler;

    /** A collection of signed state metrics. */
    private final SignedStateMetrics signedStateMetrics;

    /** Signed states are deleted on this background thread. */
    private final SignedStateGarbageCollector signedStateGarbageCollector;

    /** This dispatcher is called when a state has been fully hashed. */
    private final StateHashedTrigger stateHashedDispatcher;

    /**
     * Start empty, with no known signed states. The number of addresses in
     * platform.hashgraph.getAddressBook() must not change in the future. The addressBook must
     * contain exactly the set of members who can sign the state. A signed state is considered
     * completed when it has signatures from members with 1/3 or more of the total stake.
     *
     * @param addressBook the address book for the network
     * @param selfId the ID of this node
     * @param recoveryHandler a method that is passed a signed state during recovery mode
     * @param signer an object responsible for signing states with this node's key
     * @param signedStateMetrics a collection of signed state metrics
     */
    public SignedStateManager(
            final DispatchBuilder dispatchBuilder,
            final AddressBook addressBook,
            final NodeId selfId,
            final Consumer<SignedState> recoveryHandler,
            final HashSigner signer,
            final SignedStateMetrics signedStateMetrics) {

        this.stateHashedDispatcher =
                dispatchBuilder.getDispatcher(StateHashedTrigger.class)::dispatch;

        this.addressBook = addressBook;
        this.selfId = selfId;
        this.recoveryHandler = recoveryHandler;
        this.signer = signer;
        this.signedStateMetrics = signedStateMetrics;
        this.signedStateGarbageCollector = new SignedStateGarbageCollector(signedStateMetrics);

        this.savedSignatures =
                new StandardSequenceSet<>(
                        0,
                        settings.getState().maxAgeOfFutureStateSignatures,
                        SavedSignature::round);
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        signedStateGarbageCollector.start();
    }

    /**
     * Stop background threads used by this manager. Useful for unit tests where we don't want the
     * manager to live beyond the scope of a test.
     */
    public void stop() {
        signedStateGarbageCollector.stop();
    }

    /**
     * @return latest round for which we have a strong minority of signatures
     */
    public long getLastCompleteRound() {
        return lastCompleteSignedState.getRound();
    }

    /**
     * Get a wrapper containing the last complete signed state.
     *
     * @param strong if true then the state will be returned with a strong reservation
     * @return a wrapper with the latest complete signed state, or null if none are complete
     */
    public AutoCloseableWrapper<SignedState> getLastCompleteSignedState(final boolean strong) {
        return lastCompleteSignedState.get(strong);
    }

    /**
     * Get a wrapper containing the last signed state. May be unhashed, may or may not have all
     * required signatures. State is returned with a strong reservation.
     *
     * @return a wrapper with the latest signed state, or null if none are complete
     */
    public AutoCloseableWrapper<SignedState> getLastSignedState() {
        return lastState.get();
    }

    /**
     * Get the latest signed states stored by this manager. This method creates a copy, so no
     * changes to the array will be made
     *
     * @return the latest signed states
     */
    public List<SignedStateInfo> getSignedStateInfo() {
        // Since this method is not synchronized, it's possible we may add a state multiple times to
        // this collection.
        // The map makes sure that duplicates are not returned to the caller.
        final Map<Long, SignedState> stateMap = new HashMap<>();

        try (final AutoCloseableWrapper<SignedState> wrapper = lastCompleteSignedState.get()) {
            if (wrapper.get() != null) {
                stateMap.put(wrapper.get().getRound(), wrapper.get());
            }
        }

        freshUnsignedStates.atomicIteration(
                iterator ->
                        iterator.forEachRemaining(
                                signedState -> stateMap.put(signedState.getRound(), signedState)));

        staleUnsignedStates.atomicIteration(
                iterator ->
                        iterator.forEachRemaining(
                                signedState -> stateMap.put(signedState.getRound(), signedState)));

        // Sort the states based on round number
        final List<Long> rounds = new ArrayList<>(stateMap.keySet());
        Collections.sort(rounds);
        final List<SignedStateInfo> sortedStates = new ArrayList<>(rounds.size());
        for (final long round : rounds) {
            sortedStates.add(stateMap.get(round));
        }

        return sortedStates;
    }

    /**
     * Hash a state and start collecting signatures for it.
     *
     * @param signedState the signed state to be kept by the manager
     */
    public synchronized void addUnsignedState(final SignedState signedState) {

        signedState.setGarbageCollector(signedStateGarbageCollector);

        if (lastState.getRound() >= signedState.getRound()) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "states added to SignedStateManager in an incorrect order."
                            + "Latest state is from round {}, provided state is from round {}",
                    lastState.getRound(),
                    signedState.getRound());
            return;
        }

        hashState(signedState);
        notifyNewSignedStateBeingTracked(signedState, TRANSACTIONS);
        lastState.set(signedState);

        if (settings.isEnableStateRecovery()) {
            // Short circuit state pipeline during recovery mode
            recoveryHandler.accept(signedState);
            return;
        }

        freshUnsignedStates.put(signedState);

        final Signature signature = signer.sign(signedState.getState().getHash());
        notifyStateSelfSigned(signedState, signature);
        addSignature(signedState, selfId.getId(), signature);

        gatherSavedSignatures(signedState);

        adjustSavedSignaturesWindow(signedState.getRound());
        purgeOldUnsignedStates();
    }

    /**
     * Add a completed signed state, e.g. a state from reconnect or a state from disk.
     *
     * @param signedState the signed state to add
     * @param sourceOfSignedState the source of this signed state, should be RECONNECT or DISK
     */
    public synchronized void addCompleteSignedState(
            final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {

        signedState.setGarbageCollector(signedStateGarbageCollector);

        notifyNewSignedStateBeingTracked(signedState, sourceOfSignedState);

        if (signedState.getRound() > lastCompleteSignedState.getRound()) {
            setLastCompleteSignedState(signedState);
            purgeOldUnsignedStates();

            if (signedState.getRound() > lastState.getRound()) {
                lastState.set(signedState);
                adjustSavedSignaturesWindow(signedState.getRound());
            }
        } else {
            LOG.warn(
                    EXCEPTION.getMarker(),
                    "Latest complete signed state is from round {}, "
                            + "completed signed state for round {} rejected",
                    lastCompleteSignedState.getRound(),
                    signedState.getRound());
        }
    }

    /**
     * An observer of pre-consensus state signatures.
     *
     * @param round the round that was signed
     * @param signerId the ID of the signer
     * @param hash the hash that was signed
     * @param signature the signature on the hash
     */
    @Observer(dispatchType = PreConsensusStateSignatureTrigger.class)
    public synchronized void preConsensusSignatureObserver(
            final Long round, final Long signerId, final Hash hash, final Signature signature) {

        signedStateMetrics.getStateSignaturesGatheredPerSecondMetric().cycle();

        try (final AutoCloseableWrapper<SignedState> wrapper = getIncompleteState(round)) {

            final SignedState signedState = wrapper.get();
            if (signedState == null) {
                // This round has already been completed, or it is really old or in the future
                savedSignatures.add(new SavedSignature(round, signerId, signature));
                return;
            }

            addSignature(signedState, signerId, signature);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized AutoCloseableWrapper<SignedState> find(final long round, final Hash hash) {
        if (!lastCompleteSignedState.isNull()) {
            // Return a more recent fully signed state, if available
            if (round < lastCompleteSignedState.getRound()) {
                return lastCompleteSignedState.get(false);
            }

            // If the latest state exactly matches the request, return it
            try (final AutoCloseableWrapper<SignedState> lastComplete =
                    lastCompleteSignedState.get(false)) {
                if (stateMatches(lastComplete.get(), round, hash)) {
                    lastComplete.get().weakReserveState();
                    return lastComplete;
                }
            }
        }

        // The requested round is later than the latest fully signed state.
        // Check if any of the fresh states match the request exactly.
        return freshUnsignedStates.find(ss -> stateMatches(ss, round, hash), false);
    }

    private static boolean stateMatches(
            final SignedState signedState, final long round, final Hash hash) {
        return signedState != null
                && signedState.getRound() == round
                && signedState.getState().getHash().equals(hash);
    }

    /** Hash a signed state. */
    private void hashState(final SignedState signedState) {
        final Instant start = Instant.now();
        try {
            final Hash hash =
                    MerkleCryptoFactory.getInstance().digestTreeAsync(signedState.getState()).get();

            if (signedStateMetrics != null) {
                signedStateMetrics
                        .getSignedStateHashingTimeMetric()
                        .update(Duration.between(start, Instant.now()).toMillis());
            }

            stateHashedDispatcher.dispatch(signedState.getRound(), hash);

        } catch (final ExecutionException e) {
            fatalError("Exception occurred during SignedState hashing", e);
        } catch (final InterruptedException e) {
            LOG.error(
                    EXCEPTION.getMarker(),
                    "Interrupted while hashing state. Expect buggy behavior.");
            Thread.currentThread().interrupt();
        }
    }

    /** Mark a SignedState as the most recent completed signed state. */
    private void setLastCompleteSignedState(final SignedState signedState) {

        if (signedState.getRound() <= lastCompleteSignedState.getRound()) {
            throw new IllegalStateException(
                    "last complete signed state is from round "
                            + lastCompleteSignedState.getRound()
                            + ", cannot set last complete state from round "
                            + signedState.getRound());
        }

        lastCompleteSignedState.set(signedState);

        freshUnsignedStates.atomicIteration(
                iterator -> {
                    while (iterator.hasNext()) {
                        final SignedState next = iterator.next();

                        if (next.getRound() < signedState.getRound()) {
                            // This state is older than the most recently signed state, so it is
                            // stale now
                            staleUnsignedStates.put(next);
                            iterator.remove();
                        }
                    }
                });

        notifyNewSignedState(signedState);
    }

    /**
     * Given an iterator that walks over a collection of signed states, remove any states that are
     * too old.
     *
     * @param iterator an iterator that walks over a collection of signed states
     */
    private void removeOldStatesFromIterator(final Iterator<SignedState> iterator) {
        final long earliestPermittedRound =
                lastState.getRound() - settings.getState().roundsNonAncient + 1;
        while (iterator.hasNext()) {
            final SignedState signedState = iterator.next();
            if (signedState.getRound() < earliestPermittedRound) {
                signedStateMetrics.getTotalUnsignedStatesMetric().increment();
                notifyStateLacksSignatures(signedState);
                iterator.remove();
            }
        }
    }

    /** Get rid of unsigned states that are too old. */
    private void purgeOldUnsignedStates() {
        freshUnsignedStates.atomicIteration(this::removeOldStatesFromIterator);
        staleUnsignedStates.atomicIteration(this::removeOldStatesFromIterator);

        signedStateMetrics.getFreshStatesMetric().update(freshUnsignedStates.getSize());
        signedStateMetrics.getStaleStatesMetric().update(staleUnsignedStates.getSize());
    }

    /**
     * Get an unsigned state for a particular round, if it exists.
     *
     * @param round the round in question
     * @return a wrapper around a signed state for a round, or a wrapper around null if a signed
     *     state for that round is not present
     */
    private AutoCloseableWrapper<SignedState> getIncompleteState(final long round) {
        final AutoCloseableWrapper<SignedState> wrapper = freshUnsignedStates.get(round, false);
        if (wrapper.get() != null) {
            return wrapper;
        }
        return staleUnsignedStates.get(round, false);
    }

    /**
     * Gather and apply all signatures that were previously saved for a signed state.
     *
     * @param signedState a signed state that is now able to collect signatures
     */
    private void gatherSavedSignatures(final SignedState signedState) {
        savedSignatures.removeSequenceNumber(
                signedState.getRound(),
                savedSignature ->
                        addSignature(
                                signedState, savedSignature.memberId, savedSignature.signature));
    }

    /**
     * Adjust the window where we are willing to save future signatures.
     *
     * @param currentRound the round of the most recently signed state
     */
    private void adjustSavedSignaturesWindow(final long currentRound) {
        // Only save signatures for round N+1 and after.
        // Any rounds behind this one will either have already had a SignedState
        // added to this manager, or will never have a SignedState added to this manager.
        savedSignatures.shiftWindow(currentRound + 1);
    }

    /** Called when a signed state is first completed. */
    private void signedStateNewlyComplete(final SignedState signedState) {
        signedStateMetrics.getStatesSignedPerSecondMetric().cycle();
        signedStateMetrics
                .getAverageSigningTimeMetric()
                .update(
                        Duration.between(signedState.getCreationTimestamp(), Instant.now())
                                .toMillis());

        notifyStateHasEnoughSignatures(signedState);

        if (signedState.getRound() > lastCompleteSignedState.getRound()) {
            setLastCompleteSignedState(signedState);
        }

        freshUnsignedStates.remove(signedState.getRound());
        staleUnsignedStates.remove(signedState.getRound());
    }

    /**
     * Add a new signature to a signed state.
     *
     * @param signedState the state being signed
     * @param nodeId the ID of the signer
     * @param signature the signature on the state
     */
    private void addSignature(
            final SignedState signedState, final long nodeId, final Signature signature) {

        if (settings.isEnableStateRecovery()) {
            // recover mode no need to collect signatures
            return;
        }

        final SigSet sigSet = signedState.getSigSet();

        if (sigSet.isComplete()) {
            // No need to add more signatures
            return;
        }

        if (sigSet.getSigInfo((int) nodeId) != null) {
            // we already have this signature so nothing should be done
            return;
        }

        // public key of the other member who signed
        final PublicKey key = addressBook.getAddress(nodeId).getSigPublicKey();

        final Hash stateHash = signedState.getState().getHash();

        // Although it may be ok to skip the validation self signatures in theory,
        // in the interest of defensive programming do the check anyway.
        final boolean valid = signature.verifySignature(stateHash.getValue(), key);

        if (valid) {
            sigSet.addSigInfo(
                    new SigInfo(
                            signedState.getRound(),
                            nodeId,
                            signedState.getState().getHash(),
                            signature));
        }

        if (sigSet.isComplete()) {
            // at this point the signed state is complete for the first time
            signedStateNewlyComplete(signedState);
        }
    }

    /**
     * Send out a notification that a new signed state is being tracked.
     *
     * @param signedState the signed state now being tracked
     * @param sourceOfSignedState the source of this signed state
     */
    private void notifyNewSignedStateBeingTracked(
            final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {

        // Caller is always holding a strong reservation and this notification type is synchronous,
        // so no need to take another reservation.

        final NewSignedStateBeingTrackedNotification notification =
                new NewSignedStateBeingTrackedNotification(
                        signedState, sourceOfSignedState, selfId);
        NotificationFactory.getEngine()
                .dispatch(NewSignedStateBeingTrackedListener.class, notification);
    }

    /**
     * Send out a notification that a state has been signed by self.
     *
     * @param signedState the state that was signed
     * @param signature the self signature on the state
     */
    private void notifyStateSelfSigned(final SignedState signedState, final Signature signature) {
        final StateSelfSignedNotification notification =
                new StateSelfSignedNotification(
                        signedState.getRound(),
                        signature,
                        signedState.getState().getHash(),
                        selfId);
        NotificationFactory.getEngine().dispatch(StateSelfSignedListener.class, notification);
    }

    /**
     * Send out a notification that the most recently signed state has changed.
     *
     * @param signedState the new most recently signed state
     */
    private void notifyNewSignedState(final SignedState signedState) {
        final NewSignedStateNotification notification =
                new NewSignedStateNotification(
                        signedState.getSwirldState(),
                        signedState.getState().getSwirldDualState(),
                        signedState.getRound(),
                        signedState.getConsensusTimestamp());
        signedState.reserveState();
        NotificationFactory.getEngine()
                .dispatch(
                        NewSignedStateListener.class,
                        notification,
                        result -> signedState.releaseState());
    }

    /**
     * Send out a notification that a signed state was unable to be completely signed.
     *
     * @param signedState the state that was unable to be complete signed
     */
    private void notifyStateLacksSignatures(final SignedState signedState) {
        final StateLacksSignaturesNotification notification =
                new StateLacksSignaturesNotification(signedState, selfId);
        signedState.weakReserveState();
        NotificationFactory.getEngine()
                .dispatch(
                        StateLacksSignaturesListener.class,
                        notification,
                        result -> signedState.weakReleaseState());
    }

    /**
     * Send out a notification that a signed state was able to collect enough signatures to become
     * complete.
     *
     * @param signedState the state that now has enough signatures
     */
    private void notifyStateHasEnoughSignatures(final SignedState signedState) {
        final StateHasEnoughSignaturesNotification notification =
                new StateHasEnoughSignaturesNotification(signedState, selfId);
        signedState.weakReserveState();
        NotificationFactory.getEngine()
                .dispatch(
                        StateHasEnoughSignaturesListener.class,
                        notification,
                        result -> signedState.weakReleaseState());
    }
}

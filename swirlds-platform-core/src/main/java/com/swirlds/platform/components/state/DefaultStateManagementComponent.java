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
package com.swirlds.platform.components.state;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.output.StateSignature;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.dispatch.triggers.control.StateDumpRequestedTrigger;
import com.swirlds.platform.metrics.IssMetrics;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateInfo;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.SignedStateMetrics;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.util.HashLogger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The default implementation of {@link StateManagementComponent}. */
public class DefaultStateManagementComponent implements StateManagementComponent {

    private static final Logger logger =
            LogManager.getLogger(DefaultStateManagementComponent.class);

    private final ThreadManager threadManager;

    /** Various metrics about signed states */
    private final SignedStateMetrics signedStateMetrics;

    /** Keeps track of various signed states in various stages of collecting signatures */
    private final SignedStateManager signedStateManager;

    /** Manages the pipeline of signed states to be written to disk */
    private final SignedStateFileManager signedStateFileManager;

    /** Tracks the state hashes reported by peers and detects ISSes. */
    private final ConsensusHashManager consensusHashManager;

    /** A logger for hash stream data */
    private final HashLogger hashLogger;

    /** Builds dispatches for communication internal to this component */
    private final DispatchBuilder dispatchBuilder;
    /** Used to track signed state leaks, if enabled */
    private SignedStateSentinel signedStateSentinel;

    private final StateConfig stateConfig;

    /**
     * @param context the platform context
     * @param threadManager manages platform thread resources
     * @param addressBook the initial address book
     * @param signer an object capable of signing with the platform's private key
     * @param mainClassName the name of the app class inheriting from SwirldMain
     * @param selfId this node's id
     * @param swirldName the name of the swirld being run
     * @param prioritySystemTransactionSubmitter submits priority system transactions
     * @param stateToDiskEventConsumer consumer to invoke when a state is attempted to be written to
     *     disk
     * @param newLatestCompleteStateConsumer consumer to invoke when there is a new latest complete
     *     signed state
     * @param stateLacksSignaturesConsumer consumer to invoke when a state is about to be ejected
     *     from memory with enough signatures to be complete
     * @param stateHasEnoughSignaturesConsumer consumer to invoke when a state accumulates enough
     *     signatures to be complete
     * @param issConsumer consumer to invoke when an ISS is detected
     * @param fatalErrorConsumer consumer to invoke when a fatal error has occurred
     */
    public DefaultStateManagementComponent(
            final PlatformContext context,
            final ThreadManager threadManager,
            final AddressBook addressBook,
            final PlatformSigner signer,
            final String mainClassName,
            final NodeId selfId,
            final String swirldName,
            final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            final StateToDiskAttemptConsumer stateToDiskEventConsumer,
            final NewLatestCompleteStateConsumer newLatestCompleteStateConsumer,
            final StateLacksSignaturesConsumer stateLacksSignaturesConsumer,
            final StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer,
            final IssConsumer issConsumer,
            final HaltRequestedConsumer haltRequestedConsumer,
            final FatalErrorConsumer fatalErrorConsumer) {

        this.threadManager = threadManager;
        this.signedStateMetrics = new SignedStateMetrics(context.getMetrics());
        this.stateConfig = context.getConfiguration().getConfigData(StateConfig.class);
        this.signedStateSentinel = new SignedStateSentinel(threadManager, OSTime.getInstance());

        dispatchBuilder =
                new DispatchBuilder(
                        context.getConfiguration().getConfigData(DispatchConfiguration.class));

        hashLogger = new HashLogger(threadManager, selfId);

        signedStateFileManager =
                new SignedStateFileManager(
                        context,
                        threadManager,
                        signedStateMetrics,
                        OSTime.getInstance(),
                        mainClassName,
                        selfId,
                        swirldName,
                        stateToDiskEventConsumer);

        final StateHasEnoughSignaturesConsumer combinedStateHasEnoughSignaturesConsumer =
                ssw -> {
                    stateHasEnoughSignatures(ssw.get());
                    // This consumer releases the wrapper, so it must be last
                    stateHasEnoughSignaturesConsumer.stateHasEnoughSignatures(ssw);
                };

        final StateLacksSignaturesConsumer combinedStateLacksSignaturesConsumer =
                ssw -> {
                    stateLacksSignatures(ssw.get());
                    // This consumer releases the wrapper, so it must be last.
                    stateLacksSignaturesConsumer.stateLacksSignatures(ssw);
                };

        signedStateManager =
                new SignedStateManager(
                        threadManager,
                        dispatchBuilder,
                        addressBook,
                        selfId,
                        context.getConfiguration().getConfigData(StateConfig.class),
                        signer,
                        signedStateMetrics,
                        this::newSignedStateBeingTracked,
                        fatalErrorConsumer,
                        prioritySystemTransactionSubmitter,
                        newLatestCompleteStateConsumer,
                        combinedStateHasEnoughSignaturesConsumer,
                        combinedStateLacksSignaturesConsumer);

        consensusHashManager =
                new ConsensusHashManager(
                        OSTime.getInstance(),
                        dispatchBuilder,
                        addressBook,
                        context.getConfiguration().getConfigData(ConsensusConfig.class),
                        stateConfig);

        final IssHandler issHandler =
                new IssHandler(
                        OSTime.getInstance(),
                        dispatchBuilder,
                        stateConfig,
                        selfId.getId(),
                        haltRequestedConsumer,
                        fatalErrorConsumer,
                        issConsumer);

        final IssMetrics issMetrics = new IssMetrics(context.getMetrics(), addressBook);

        dispatchBuilder
                .registerObservers(issHandler)
                .registerObservers(consensusHashManager)
                .registerObservers(issMetrics)
                .registerObservers(this);
    }

    /**
     * Handles a signed state that is now complete by saving it to disk, if it should be saved.
     *
     * @param signedState the newly complete signed state
     */
    private void stateHasEnoughSignatures(final SignedState signedState) {
        if (signedState.isStateToSave()) {
            signedStateFileManager.saveSignedStateToDisk(signedState);
        }
    }

    /**
     * Handles a signed state that did not collect enough signatures before being ejected from
     * memory.
     *
     * @param signedState the signed state that lacks signatures
     */
    private void stateLacksSignatures(final SignedState signedState) {
        if (signedState.isStateToSave()) {
            final long previousCount = signedStateMetrics.getTotalUnsignedDiskStatesMetric().get();
            signedStateMetrics.getTotalUnsignedDiskStatesMetric().increment();
            final long newCount = signedStateMetrics.getTotalUnsignedDiskStatesMetric().get();

            if (newCount <= previousCount) {
                logger.error(
                        EXCEPTION.getMarker(), "Metric for total unsigned disk states not updated");
            }

            logger.error(
                    EXCEPTION.getMarker(),
                    "state written to disk for round {} did not have enough signatures. Collected"
                        + " signatures representing {}/{} stake. Total unsigned disk states so far:"
                        + " {}. AB={}",
                    signedState.getRound(),
                    signedState.getSigningStake(),
                    signedState.getAddressBook().getTotalStake(),
                    newCount,
                    signedState.getAddressBook());
            signedStateFileManager.saveSignedStateToDisk(signedState);
        }
    }

    private void newSignedStateBeingTracked(
            final SignedState signedState, final SourceOfSignedState source) {
        // When we begin tracking a new signed state, "introduce" the state to the
        // SignedStateFileManager
        if (source == SourceOfSignedState.DISK) {
            signedStateFileManager.registerSignedStateFromDisk(signedState);
        } else {
            signedStateFileManager.determineIfStateShouldBeSaved(signedState);
        }

        if (signedState.getState().getHash() != null) {
            hashLogger.logHashes(signedState);
        }
    }

    @Override
    public void newSignedStateFromTransactions(final SignedState signedState) {
        signedStateManager.addUnsignedState(signedState);
    }

    @Override
    public void handleStateSignature(
            final StateSignature stateSignature, final boolean isConsensus) {
        if (isConsensus) {
            consensusHashManager.postConsensusSignatureObserver(
                    stateSignature.round(), stateSignature.signerId(), stateSignature.stateHash());
        } else {
            signedStateManager.preConsensusSignatureObserver(
                    stateSignature.round(), stateSignature.signerId(), stateSignature.signature());
        }
    }

    @Override
    public AutoCloseableWrapper<SignedState> getLatestSignedState(final boolean strongReservation) {
        return signedStateManager.getLatestSignedState(strongReservation);
    }

    @Override
    public AutoCloseableWrapper<SignedState> getLatestImmutableState() {
        return signedStateManager.getLatestImmutableState();
    }

    @Override
    public long getLastCompleteRound() {
        return signedStateManager.getLastCompleteRound();
    }

    @Override
    public long getLastRoundSavedToDisk() {
        return signedStateFileManager.getLastRoundSavedToDisk();
    }

    @Override
    public List<SignedStateInfo> getSignedStateInfo() {
        return signedStateManager.getSignedStateInfo();
    }

    @Override
    public void stateToLoad(
            final SignedState signedState, final SourceOfSignedState sourceOfSignedState) {
        signedStateManager.addCompleteSignedState(signedState, sourceOfSignedState);
    }

    @Override
    public void roundAppliedToState(final long round) {
        consensusHashManager.roundCompleted(round);
    }

    @Override
    public void start() {
        signedStateManager.start();
        signedStateFileManager.start();
        dispatchBuilder.start();

        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.start();
        }
    }

    @Override
    public void stop() {
        signedStateManager.stop();
        signedStateFileManager.stop();
        if (stateConfig.signedStateSentinelEnabled()) {
            signedStateSentinel.stop();
        }
    }

    @Override
    public void onFatalError() {
        if (stateConfig.dumpStateOnFatal()) {
            try (final AutoCloseableWrapper<SignedState> wrapper =
                    signedStateManager.getLatestSignedState(false)) {
                final SignedState state = wrapper.get();
                if (state != null) {
                    signedStateFileManager.dumpState(state, "fatal", true);
                }
            }
        }
    }

    @Override
    public AutoCloseableWrapper<SignedState> find(final long round, final Hash hash) {
        return signedStateManager.find(round, hash);
    }

    /**
     * This observer is called when the most recent signed state is requested to be dumped to disk.
     *
     * @param reason reason why the state is being dumped, e.g. "fatal" or "iss". Is used as a part
     *     of the file path for the dumped state files, so this string should not contain any
     *     special characters or whitespace.
     * @param blocking if this method should block until the operation has been completed
     */
    @Observer(StateDumpRequestedTrigger.class)
    public void stateDumpRequestedObserver(final String reason, final Boolean blocking) {
        try (final AutoCloseableWrapper<SignedState> wrapper =
                signedStateManager.getLatestImmutableState()) {
            signedStateFileManager.dumpState(wrapper.get(), reason, blocking);
        }
    }
}

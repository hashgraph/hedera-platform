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
package com.swirlds.platform.state.iss;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_HASH;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.sequence.map.ConcurrentSequenceMap;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.logging.payloads.IssPayload;
import com.swirlds.platform.Settings;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.error.CatastrophicIssTrigger;
import com.swirlds.platform.dispatch.triggers.error.SelfIssTrigger;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.RoundCompletedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.dispatch.triggers.transaction.PostConsensusStateSignatureTrigger;
import com.swirlds.platform.state.iss.internal.ConsensusHashFinder;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.platform.state.iss.internal.RoundHashValidator;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps track of the state hashes reported by all network nodes. Responsible for detecting ISS
 * events.
 */
public class ConsensusHashManager {

    private static final Logger LOG = LogManager.getLogger(ConsensusHashManager.class);

    private final SequenceMap<Long /* round */, RoundHashValidator> roundData;

    private long previousRound = -1;

    /** The address book of this network. */
    final AddressBook addressBook;

    /** Prevent log messages about a lack of signatures from spamming the logs. */
    private final RateLimiter lackingSignaturesRateLimiter =
            new RateLimiter(
                    Duration.ofSeconds(Settings.getInstance().getState().secondsBetweenIssLogs));

    /** Prevent log messages about self ISS events from spamming the logs. */
    private final RateLimiter selfIssRateLimiter =
            new RateLimiter(
                    Duration.ofSeconds(Settings.getInstance().getState().secondsBetweenIssLogs));

    /** Prevent log messages about catastrophic ISS events from spamming the logs. */
    private final RateLimiter catastrophicIssRateLimiter =
            new RateLimiter(
                    Duration.ofSeconds(Settings.getInstance().getState().secondsBetweenIssLogs));

    private final SelfIssTrigger selfIssDispatcher;
    private final CatastrophicIssTrigger catastrophicIssDispatcher;
    private final StateHashValidityTrigger stateHashValidityDispatcher;

    /**
     * Create an object that tracks reported hashes and detects ISS events.
     *
     * @param dispatchBuilder responsible for building dispatchers
     * @param addressBook the address book for the network
     */
    public ConsensusHashManager(
            final DispatchBuilder dispatchBuilder, final AddressBook addressBook) {

        this.selfIssDispatcher = dispatchBuilder.getDispatcher(SelfIssTrigger.class)::dispatch;
        this.catastrophicIssDispatcher =
                dispatchBuilder.getDispatcher(CatastrophicIssTrigger.class)::dispatch;
        this.stateHashValidityDispatcher =
                dispatchBuilder.getDispatcher(StateHashValidityTrigger.class)::dispatch;

        this.addressBook = addressBook;

        this.roundData =
                new ConcurrentSequenceMap<>(
                        -Settings.getInstance().getState().roundsNonAncient,
                        Settings.getInstance().getState().roundsNonAncient,
                        x -> x);
    }

    /**
     * Observes when a round has been completed.
     *
     * @param round the round that was just completed
     */
    @Observer(dispatchType = RoundCompletedTrigger.class)
    public void roundCompletedObserver(final Long round) {
        if (round <= previousRound) {
            throw new IllegalArgumentException(
                    "previous round was " + previousRound + ", can't decrease round to " + round);
        }

        final long oldestRoundToValidate = round - roundData.getSequenceNumberCapacity() + 1;

        if (round != previousRound + 1) {
            // We are either loading the first state at boot time, or we had a reconnect that caused
            // us to skip some
            // rounds. Rounds that have not yet been validated at this point in time should not be
            // considered
            // evidence of a catastrophic ISS.
            roundData.shiftWindow(oldestRoundToValidate);
        } else {
            roundData.shiftWindow(oldestRoundToValidate, this::handleRemovedRound);
        }

        final long roundStake = addressBook.getTotalStake();
        previousRound = round;
        roundData.put(
                round, new RoundHashValidator(stateHashValidityDispatcher, round, roundStake));
    }

    /**
     * Handle a round that has become old enough that we want to stop tracking data on it.
     *
     * @param round the round that is old
     * @param roundHashValidator the hash validator for the round
     */
    private void handleRemovedRound(final long round, final RoundHashValidator roundHashValidator) {
        final boolean justDecided = roundHashValidator.outOfTime();

        final StringBuilder sb = new StringBuilder();
        roundHashValidator.getHashFinder().writePartitionData(sb);
        LOG.info(STATE_HASH.getMarker(), sb);

        if (justDecided) {
            final HashValidityStatus status = roundHashValidator.getStatus();
            if (status == HashValidityStatus.CATASTROPHIC_ISS
                    || status == HashValidityStatus.CATASTROPHIC_LACK_OF_DATA) {
                handleCatastrophic(roundHashValidator);
            } else if (status == HashValidityStatus.LACK_OF_DATA) {
                handleLackOfData(roundHashValidator);
            } else {
                throw new IllegalStateException(
                        "Unexpected hash validation status "
                                + status
                                + ", should have decided prior to now");
            }
        }
    }

    /**
     * Observes post-consensus state signature transactions.
     *
     * <p>Since it is only possible to sign a round after it has reached consensus, it is guaranteed
     * that any valid signature transaction observed here (post consensus) will be for a round in
     * the past.
     *
     * @param round the round that was signed
     * @param signerId the ID of the signer
     * @param hash the hash that was signed
     * @param signature the signature on the hash
     */
    @Observer(dispatchType = PostConsensusStateSignatureTrigger.class)
    public void postConsensusSignatureObserver(
            final Long round, final Long signerId, final Hash hash, final Signature signature) {

        final long nodeStake = addressBook.getAddress(signerId).getStake();

        final RoundHashValidator roundValidator = roundData.get(round);
        if (roundValidator == null) {
            // We are being asked to validate a signature from the far future or far past, or a
            // round that has already
            // been decided.
            return;
        }

        final boolean decided = roundValidator.reportHashFromNetwork(signerId, nodeStake, hash);
        if (decided) {
            checkValidity(roundValidator);
        }
    }

    /**
     * Observe when this node finishes hashing a state.
     *
     * @param round the round of the state
     * @param hash the hash of the state
     */
    @Observer(dispatchType = StateHashedTrigger.class)
    public void stateHashedObserver(final Long round, final Hash hash) {
        final RoundHashValidator roundHashValidator = roundData.get(round);
        if (roundHashValidator == null) {
            throw new IllegalStateException(
                    "Hash reported for round " + round + ", but that round is not being tracked");
        }

        final boolean decided = roundHashValidator.reportSelfHash(hash);
        if (decided) {
            checkValidity(roundHashValidator);
        }
    }

    /**
     * Observe when a state is obtained via reconnect.
     *
     * @param round the round of the state that was obtained
     * @param stateHash the hash of the state that was obtained
     */
    @Observer(dispatchType = ReconnectStateLoadedTrigger.class)
    public void reconnectStateLoadedObserver(final Long round, final Hash stateHash) {
        roundCompletedObserver(round);
        stateHashedObserver(round, stateHash);
    }

    /**
     * Observe when a state is loaded from disk.
     *
     * @param round the round of the state that was loaded
     * @param stateHash the hash of the state that was loaded
     */
    @Observer(dispatchType = DiskStateLoadedTrigger.class)
    public void diskStateLoadedObserver(final Long round, final Hash stateHash) {
        roundCompletedObserver(round);
        stateHashedObserver(round, stateHash);
    }

    /**
     * Called once the validity has been decided. Take action based on the validity status.
     *
     * @param roundValidator the validator for the round
     */
    private void checkValidity(final RoundHashValidator roundValidator) {
        final long round = roundValidator.getRound();

        switch (roundValidator.getStatus()) {
            case VALID -> {
                // :)
            }
            case SELF_ISS -> handleSelfIss(roundValidator);
            case CATASTROPHIC_ISS -> handleCatastrophic(roundValidator);
            case UNDECIDED -> throw new IllegalStateException(
                    "status is undecided, but method reported a decision, round = " + round);
            case LACK_OF_DATA -> throw new IllegalStateException(
                    "a decision that we lack data should only be possible once time runs out, round"
                            + " = "
                            + round);
            default -> throw new IllegalStateException(
                    "unhandled case " + roundValidator.getStatus() + ", round = " + round);
        }
    }

    /**
     * This node doesn't agree with the consensus hash.
     *
     * @param roundHashValidator the validator responsible for validating the round with a self ISS
     */
    private void handleSelfIss(final RoundHashValidator roundHashValidator) {
        final long round = roundHashValidator.getRound();
        final Hash selfHash = roundHashValidator.getSelfStateHash();
        final Hash consensusHash = roundHashValidator.getConsensusHash();

        final long skipCount = selfIssRateLimiter.getDeniedRequests();
        if (selfIssRateLimiter.request()) {

            final StringBuilder sb = new StringBuilder();
            sb.append("Invalid State Signature (ISS): this node has the wrong hash for round ")
                    .append(round)
                    .append(".\n");

            roundHashValidator.getHashFinder().writePartitionData(sb);
            writeSkippedLogCount(sb, skipCount);

            LOG.fatal(
                    EXCEPTION.getMarker(),
                    new IssPayload(
                            sb.toString(),
                            round,
                            selfHash.toString(),
                            consensusHash.toString(),
                            false));
        }

        selfIssDispatcher.dispatch(round, selfHash, consensusHash);
    }

    /**
     * There has been a catastrophic ISS or a catastrophic lack of data.
     *
     * @param roundHashValidator information about the round, including the signatures that were
     *     gathered
     */
    private void handleCatastrophic(final RoundHashValidator roundHashValidator) {

        final long round = roundHashValidator.getRound();
        final ConsensusHashFinder hashFinder = roundHashValidator.getHashFinder();
        final Hash selfHash = roundHashValidator.getSelfStateHash();

        final long skipCount = catastrophicIssRateLimiter.getDeniedRequests();
        if (catastrophicIssRateLimiter.request()) {

            final StringBuilder sb = new StringBuilder();
            sb.append("Catastrophic Invalid State Signature (ISS)\n");
            sb.append(
                    "Due to divergence in state hash between many network members, this network is"
                            + " incapable of continued operation without human intervention.\n");

            hashFinder.writePartitionData(sb);
            writeSkippedLogCount(sb, skipCount);

            LOG.fatal(
                    EXCEPTION.getMarker(),
                    new IssPayload(sb.toString(), round, selfHash.toString(), "", true));
        }

        catastrophicIssDispatcher.dispatch(round, selfHash);
    }

    /**
     * We are not getting the signatures we need to be getting. ISS events may be going undetected.
     *
     * @param roundHashValidator information about the round
     */
    private void handleLackOfData(final RoundHashValidator roundHashValidator) {
        final long skipCount = lackingSignaturesRateLimiter.getDeniedRequests();
        if (!lackingSignaturesRateLimiter.request()) {
            return;
        }

        final long round = roundHashValidator.getRound();
        final ConsensusHashFinder hashFinder = roundHashValidator.getHashFinder();
        final Hash selfHash = roundHashValidator.getSelfStateHash();

        final StringBuilder sb = new StringBuilder();
        sb.append("Unable to collect enough data to determine the consensus hash for round ")
                .append(round)
                .append(".\n");
        if (selfHash == null) {
            sb.append("No self hash was computed. This is highly unusual.\n");
        }
        hashFinder.writePartitionData(sb);
        writeSkippedLogCount(sb, skipCount);

        LOG.warn(STATE_HASH.getMarker(), sb);
    }

    /** Write the number of times a log has been skipped. */
    private static void writeSkippedLogCount(final StringBuilder sb, final long skipCount) {
        if (skipCount > 0) {
            sb.append("This condition has been triggered ")
                    .append(skipCount)
                    .append(" time(s) over the last ")
                    .append(Duration.ofMinutes(1).toSeconds())
                    .append("seconds.");
        }
    }
}

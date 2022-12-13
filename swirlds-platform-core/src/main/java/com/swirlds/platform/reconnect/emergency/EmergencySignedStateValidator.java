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
package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.reconnect.ReconnectException;
import com.swirlds.platform.reconnect.SignedStateValidator;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignatureSummary;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the signed state received by the learner in an emergency reconnect. If the received
 * state has the exact round and hash requested, it does not need to be fully signed. If the state
 * is for a later round, it must be signed by at least half the network stake to be considered
 * valid. The emergency reconnect scenario is described in disaster-recovery.md.
 */
public class EmergencySignedStateValidator implements SignedStateValidator {
    private static final Logger LOG = LogManager.getLogger();
    private final Crypto crypto;
    private final EmergencyRecoveryFile emergencyRecoveryFile;

    /**
     * @param crypto the object that contains all key pairs and CSPRNG state for this member
     * @param emergencyRecoveryFile the emergency recovery file
     */
    public EmergencySignedStateValidator(
            final Crypto crypto, final EmergencyRecoveryFile emergencyRecoveryFile) {
        this.crypto = crypto;
        this.emergencyRecoveryFile = emergencyRecoveryFile;
    }

    /** {@inheritDoc} */
    @Override
    public void validate(final SignedState signedState, final AddressBook addressBook)
            throws ReconnectException {
        LOG.info(
                RECONNECT.getMarker(),
                "Requested round {} with hash {}, received round {} with hash {}",
                emergencyRecoveryFile.round(),
                emergencyRecoveryFile.hash(),
                signedState.getRound(),
                signedState.getState().getHash());

        if (signedState.getRound() > emergencyRecoveryFile.round()) {
            verifyStateHasMajoritySigs(signedState, addressBook);
        } else if (signedState.getRound() < emergencyRecoveryFile.round()) {
            throwStateTooOld(signedState);
        } else {
            verifyStateHashMatches(signedState);
        }
    }

    private void verifyStateHashMatches(final SignedState signedState) {
        if (!signedState.getState().getHash().equals(emergencyRecoveryFile.hash())) {
            LOG.error(
                    RECONNECT.getMarker(),
                    "Round matches but hash does not.\nFailed emergency reconnect state:\n{}\n{}",
                    () -> signedState.getState().getPlatformState().getInfoString(),
                    () ->
                            generateHashDebugString(
                                    signedState.getState(), StateSettings.getDebugHashDepth()));

            throw new ReconnectException(
                    String.format(
                            "Received signed state for the requested round but "
                                    + "the hash does not match. Requested %s, received %s",
                            emergencyRecoveryFile.hash(), signedState.getState().getHash()));
        }
        LOG.info(
                RECONNECT.getMarker(),
                "Received a state for the requested round and hash. Validation succeeded.");
    }

    private void throwStateTooOld(final SignedState signedState) {
        LOG.error(
                RECONNECT.getMarker(),
                "State is too old.\nFailed emergency reconnect state:\n{}\n{}",
                () -> signedState.getState().getPlatformState().getInfoString(),
                () ->
                        generateHashDebugString(
                                signedState.getState(), StateSettings.getDebugHashDepth()));

        throw new ReconnectException(
                String.format(
                        "Received signed state for a round smaller than requested. Requested %d,"
                                + " received %d",
                        emergencyRecoveryFile.round(), signedState.getRound()));
    }

    private void verifyStateHasMajoritySigs(
            final SignedState signedState, final AddressBook addressBook) {
        LOG.info(
                RECONNECT.getMarker(),
                "Received round later than requested. Validating that the state is signed by a"
                        + " majority stake.");

        final SignatureSummary sigSummary =
                SignedStateUtilities.getSigningStake(signedState, crypto, addressBook);
        final long validStake = sigSummary.validStake();
        final boolean hasEnoughStake =
                Utilities.isMajority(validStake, addressBook.getTotalStake());

        SignedStateValidator.logStakeInfo(LOG, validStake, hasEnoughStake, addressBook);

        if (!hasEnoughStake) {
            LOG.error(
                    RECONNECT.getMarker(),
                    "Failed emergency reconnect state:\n{}\n{}",
                    () -> signedState.getState().getPlatformState().getInfoString(),
                    () ->
                            generateHashDebugString(
                                    signedState.getState(), StateSettings.getDebugHashDepth()));

            throw new ReconnectException(
                    String.format(
                            "Received signed state does not have enough valid signatures!"
                                    + " validCount: %d, addressBook size: %d, validStake: %d,"
                                    + " addressBook total stake: %d",
                            sigSummary.numValidSigs(),
                            addressBook.getSize(),
                            validStake,
                            addressBook.getTotalStake()));
        }
        LOG.info(
                RECONNECT.getMarker(),
                "Received a later, fully signed state. Validation succeeded.");
    }
}

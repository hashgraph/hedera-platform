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
package com.swirlds.platform.reconnect;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.generateHashDebugString;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.state.signed.SignatureSummary;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates a signed state by summing the amount of stake held by the valid signatures on the
 * state.
 */
public class DefaultSignedStateValidator implements SignedStateValidator {

    private final Crypto crypto;

    public DefaultSignedStateValidator(final Crypto crypto) {
        this.crypto = crypto;
    }

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger LOG = LogManager.getLogger();

    /**
     * Determines if a signed state is valid. If the total stake among valid signatures is a
     * majority, then the signed state is valid. If the state is not valid, an exception is thrown.
     *
     * @param signedState the signed state to validate
     * @param addressBook the address book to use to lookup public keys and stake values
     * @throws ReconnectException if the state is not valid
     */
    public void validate(final SignedState signedState, final AddressBook addressBook)
            throws ReconnectException {
        LOG.info(RECONNECT.getMarker(), "Validating signatures of the received state");

        final SignatureSummary sigSummary =
                SignedStateUtilities.getSigningStake(signedState, crypto, addressBook);

        final long validStake = sigSummary.validStake();
        final long validCount = sigSummary.numValidSigs();

        final boolean hasEnoughStake =
                Utilities.isMajority(validStake, addressBook.getTotalStake());

        SignedStateValidator.logStakeInfo(LOG, validStake, hasEnoughStake, addressBook);

        if (!hasEnoughStake) {
            LOG.error(
                    RECONNECT.getMarker(),
                    "Information for failed reconnect state:\n{}\n{}",
                    () -> signedState.getState().getPlatformState().getInfoString(),
                    () ->
                            generateHashDebugString(
                                    signedState.getState(), StateSettings.getDebugHashDepth()));
            throw new ReconnectException(
                    String.format(
                            "Error: Received signed state does not have enough valid signatures!"
                                + " validCount:%d, addressBook size:%d, validStake:%d, addressBook"
                                + " total stake:%d",
                            validCount,
                            addressBook.getSize(),
                            validStake,
                            addressBook.getTotalStake()));
        }

        LOG.info(RECONNECT.getMarker(), "State is valid");
    }
}

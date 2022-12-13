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

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.signed.SignedState;
import org.apache.logging.log4j.Logger;

/** Validates a signed state received via reconnect. */
public interface SignedStateValidator {

    /**
     * Determines if a signed state received via reconnect is valid with the address book.
     * Validation usually includes verifying that the signed state is signed with a sufficient
     * number of valid signatures to meet a certain staking threshold, but other requirements could
     * be included in validation as well.
     *
     * @param signedState the signed state to validate
     * @param addressBook the address book used for this signed state
     * @throws ReconnectException is the signed state is not valid
     */
    void validate(final SignedState signedState, final AddressBook addressBook)
            throws ReconnectException;

    /**
     * Logs information about the signed state validity.
     *
     * @param log the logger to use
     * @param validStake the amount of stake from valid signatures
     * @param hasEnoughStake true if the state has enough valid stake
     * @param addressBook the address book used to validate the signed state
     */
    static void logStakeInfo(
            final Logger log,
            final long validStake,
            final boolean hasEnoughStake,
            final AddressBook addressBook) {
        log.info(RECONNECT.getMarker(), "Signed State valid Stake :{} ", validStake);
        log.info(RECONNECT.getMarker(), "AddressBook Stake :{} ", addressBook.getTotalStake());
        log.info(RECONNECT.getMarker(), "StrongMinority status: {}", hasEnoughStake);
    }
}

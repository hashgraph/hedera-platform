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
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SIGNED_STATE;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.Crypto;
import java.security.PublicKey;
import java.util.concurrent.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utilities for evaluating signed states */
public final class SignedStateUtilities {
    private static final Logger LOG = LogManager.getLogger(SignedStateUtilities.class);

    private SignedStateUtilities() {}

    /**
     * Calculates the amount of stake among valid signatures on the signed state.
     *
     * @param signedState the signed state to calculate stake on
     * @param crypto the object capable of determining the validity of member signatures
     * @param addressBook the address book used for the signed state
     * @return the signature summary of the signed state
     */
    public static SignatureSummary getSigningStake(
            final SignedState signedState, final Crypto crypto, final AddressBook addressBook) {

        final Future<Boolean>[] maybeValidFutures =
                getValidFutures(signedState, crypto, addressBook);

        // how many valid signatures
        int validCount = 0;

        // how much stake owned by members with valid signatures
        long validStake = 0;

        final int numSigs = signedState.getSigSet().getNumMembers();
        for (int i = 0; i < numSigs; i++) {
            if (maybeValidFutures[i] == null) {
                continue;
            }
            try {
                if (maybeValidFutures[i].get()) {
                    validCount++;
                    validStake += signedState.getAddressBook().getAddress(i).getStake();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn(RECONNECT.getMarker(), "interrupted while validating signatures");
            } catch (final Exception e) {
                LOG.info(
                        EXCEPTION.getMarker(),
                        "Error while validating signature from received state: ",
                        e);
            }
        }
        return new SignatureSummary(maybeValidFutures.length, validCount, validStake);
    }

    /**
     * Calculates the validity of each signature on the {@code signedState} and returns them as a
     * list of {@link Future} objects.
     *
     * @param signedState the signed state to evaluate
     * @param addressBook the address book to use for signature verification
     * @return a list of {@link Future}s indicating the validity of each signature
     */
    private static Future<Boolean>[] getValidFutures(
            final SignedState signedState, final Crypto crypto, final AddressBook addressBook) {
        final int numSigs = signedState.getSigSet().getNumMembers();
        final Future<Boolean>[] validFutures = new Future[numSigs];
        for (int i = 0; i < numSigs; i++) {
            final PublicKey key = addressBook.getAddress(i).getSigPublicKey();
            final SigInfo sigInfo = signedState.getSigSet().getSigInfo(i);
            if (sigInfo == null) {
                continue;
            }
            validFutures[i] =
                    crypto.verifySignatureParallel(
                            signedState.getState().getHash().getValue(),
                            sigInfo.getSignature().getSignatureBytes(),
                            key,
                            (Boolean b) -> {});
        }
        return validFutures;
    }

    /**
     * Wraps a signed state in an {@link AutoCloseableWrapper} with a reservation taken out on it.
     *
     * @param signedState the signed state to wrap. Null values are permitted
     * @param strong if true then take a strong reservation (i.e. a reservation that prevents
     *     archival), otherwise take a weak reservation
     * @return an autocloseable wrapper with the state, when closed the state is released
     */
    public static AutoCloseableWrapper<SignedState> newSignedStateWrapper(
            final SignedState signedState, final boolean strong) {
        if (signedState != null) {
            if (strong) {
                signedState.reserveState();
            } else {
                signedState.weakReserveState();
            }
        }

        return new AutoCloseableWrapper<>(
                signedState,
                () -> {
                    if (signedState != null) {
                        if (strong) {
                            signedState.releaseState();
                        } else {
                            signedState.weakReleaseState();
                        }
                    }
                });
    }

    /**
     * Logs information about the signed state validity.
     *
     * @param log the logger to use
     * @param validStake the amount of stake from valid signatures
     * @param hasEnoughStake true if the state has enough valid stake
     * @param addressBook the address book used to validate the signed state
     */
    public static void logStakeInfo(
            final Logger log,
            final long validStake,
            final boolean hasEnoughStake,
            final AddressBook addressBook) {
        log.info(SIGNED_STATE.getMarker(), "Signed State valid Stake: {} ", validStake);
        log.info(SIGNED_STATE.getMarker(), "AddressBook Stake: {} ", addressBook.getTotalStake());
        log.info(SIGNED_STATE.getMarker(), "Sufficient signatures status: {}", hasEnoughStake);
    }
}

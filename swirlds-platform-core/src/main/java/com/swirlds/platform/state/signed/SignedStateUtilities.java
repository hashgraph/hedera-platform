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

import com.swirlds.common.utility.AutoCloseableWrapper;

/** {@link SignedState} utilities. */
public final class SignedStateUtilities {

    private SignedStateUtilities() {}

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
}

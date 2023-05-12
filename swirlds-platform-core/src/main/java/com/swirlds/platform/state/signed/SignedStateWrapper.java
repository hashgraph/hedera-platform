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

import com.swirlds.common.Releasable;

/** Create a wrapper around a signed state that can properly release the state when finished. */
public class SignedStateWrapper implements Releasable {

    private final SignedState signedState;
    private final boolean strong;
    private boolean isReleased;

    /**
     * Create a new wrapped signed state. Automatically takes a reservation on a state in this
     * constructor, and properly releases the state when this object is released.
     *
     * @param signedState the signed state
     * @param strong if a strong reservation is held
     */
    public SignedStateWrapper(final SignedState signedState, final boolean strong) {
        this.signedState = signedState;
        if (signedState != null) {
            if (strong) {
                signedState.reserveState();
            } else {
                signedState.weakReserveState();
            }
        }
        this.strong = strong;
    }

    /** Get the signed state. */
    public SignedState get() {
        throwIfDestroyed();
        return signedState;
    }

    /** {@inheritDoc} */
    @Override
    public boolean release() {
        throwIfDestroyed();
        isReleased = true;
        if (signedState != null) {
            if (strong) {
                signedState.releaseState();
            } else {
                signedState.weakReleaseState();
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDestroyed() {
        return isReleased;
    }
}

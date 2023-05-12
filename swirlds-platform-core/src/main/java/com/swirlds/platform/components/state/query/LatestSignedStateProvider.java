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
package com.swirlds.platform.components.state.query;

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;

/** Provides the latest complete signed state, or null if none is available. */
public interface LatestSignedStateProvider {

    /**
     * Returns the latest complete (fully signed) state with either a strong or weak reservation
     * that is released when the {@link AutoCloseableWrapper} is closed.
     *
     * @param strongReservation if {@code true}, the enclosed signed state will have a strong
     *     reservation, otherwise it will have a weak reservation.
     * @return an auto-closeable with the latest complete state, or an auto-closeable wrapper with
     *     {@code null} if none is available.
     */
    AutoCloseableWrapper<SignedState> getLatestSignedState(boolean strongReservation);
}

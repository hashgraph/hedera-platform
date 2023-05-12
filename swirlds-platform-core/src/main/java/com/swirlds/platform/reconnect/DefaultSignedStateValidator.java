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

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;

/**
 * Validates a signed state by summing the amount of stake held by the valid signatures on the
 * state.
 */
public class DefaultSignedStateValidator implements SignedStateValidator {

    public DefaultSignedStateValidator() {}

    /** {@inheritDoc} */
    public void validate(final SignedState signedState, final AddressBook addressBook) {
        signedState.pruneInvalidSignatures(addressBook);
        signedState.throwIfIncomplete();
    }
}

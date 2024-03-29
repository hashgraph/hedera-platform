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
package com.swirlds.platform.dispatch.triggers.error;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.dispatch.types.TriggerTwo;

/**
 * Trigger warning.
 *
 * <p>Sends dispatches for catastrophic ISS events.
 */
@FunctionalInterface
public interface CatastrophicIssTrigger extends TriggerTwo<Long, Hash> {

    /**
     * Signal that there has been a catastrophic ISS.
     *
     * @param round the round of the ISS
     * @param selfStateHash the hash computed by this node
     */
    @Override
    void dispatch(Long round, Hash selfStateHash);
}

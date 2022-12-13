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
package com.swirlds.platform.test.event;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.platform.internal.EventImpl;

@ConstructableIgnored
public class SimpleEvent extends EventImpl {
    private final long id;
    private boolean lastInRoundReceived;

    public SimpleEvent(final long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public void setLastInRoundReceived(final boolean lastInRoundReceived) {
        this.lastInRoundReceived = lastInRoundReceived;
    }

    @Override
    public boolean isLastInRoundReceived() {
        return lastInRoundReceived;
    }

    @Override
    public boolean isLastOneBeforeShutdown() {
        return false;
    }

    @Override
    public long getRoundReceived() {
        return 1;
    }
}

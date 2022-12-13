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
package com.swirlds.platform.test.sync;

import org.apache.commons.lang3.builder.ToStringBuilder;

/** Data holder for parameters needed for every sync unit test. */
public class SyncTestParams {

    private final int numNetworkNodes;
    private final int numCommonEvents;
    private final int numCallerEvents;
    private final int numListenerEvents;
    private final Long customSeed;

    public SyncTestParams(
            int numNetworkNodes,
            int numCommonEvents,
            int numCallerEvents,
            int numListenerEvents,
            Long customSeed) {
        this.numNetworkNodes = numNetworkNodes;
        this.numCommonEvents = numCommonEvents;
        this.numCallerEvents = numCallerEvents;
        this.numListenerEvents = numListenerEvents;
        this.customSeed = customSeed;
    }

    public SyncTestParams(
            final int numNetworkNodes,
            final int numCommonEvents,
            final int numCallerEvents,
            final int numListenerEvents) {
        this(numNetworkNodes, numCommonEvents, numCallerEvents, numListenerEvents, null);
    }

    /** The number of nodes in the network. */
    public int getNumNetworkNodes() {
        return numNetworkNodes;
    }

    /** The number of common events to insert into each node's shadow graph. */
    public int getNumCommonEvents() {
        return numCommonEvents;
    }

    /**
     * The number of events to insert into the caller's shadow graph in addition to {@link
     * SyncTestParams#numCommonEvents}.
     */
    public int getNumCallerEvents() {
        return numCallerEvents;
    }

    /**
     * The number of events to insert into the listener's shadow graph in addition to {@link
     * SyncTestParams#numCommonEvents}.
     */
    public int getNumListenerEvents() {
        return numListenerEvents;
    }

    /**
     * @return the custom seed set for this test, returns null if none is set
     */
    public Long getCustomSeed() {
        return customSeed;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("numNetworkNodes", numNetworkNodes)
                .append("numCommonEvents", numCommonEvents)
                .append("numCallerEvents", numCallerEvents)
                .append("numListenerEvents", numListenerEvents)
                .append("customSeed", customSeed)
                .toString();
    }
}

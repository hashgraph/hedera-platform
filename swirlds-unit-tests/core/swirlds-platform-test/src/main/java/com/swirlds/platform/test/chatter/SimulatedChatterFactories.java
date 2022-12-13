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
package com.swirlds.platform.test.chatter;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.PlatformMetricsFactory;
import com.swirlds.platform.chatter.ChatterSubSetting;
import com.swirlds.platform.chatter.protocol.ChatterCore;
import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class SimulatedChatterFactories implements SimulatedChatterFactory {
    private static final SimulatedChatterFactory SINGLETON = new SimulatedChatterFactories();

    public static SimulatedChatterFactory getInstance() {
        return SINGLETON;
    }

    @Override
    public SimulatedChatter build(
            final long selfId,
            final Iterable<Long> nodeIds,
            final GossipEventObserver eventTracker,
            final Supplier<Instant> now) {
        final ChatterCore<ChatterEvent> core =
                new ChatterCore<>(
                        ChatterEvent.class,
                        e -> {},
                        new ChatterSubSetting(),
                        (nodeId, ping) -> {},
                        new Metrics(
                                Executors.newSingleThreadScheduledExecutor(),
                                new PlatformMetricsFactory()));
        final EventDedup dedup =
                new EventDedup(List.of(core::eventReceived, eventTracker::newEvent));
        for (final Long nodeId : nodeIds) {
            core.newPeerInstance(nodeId, dedup);
        }

        return new ChatterWrapper(core, List.of(core, dedup));
    }
}

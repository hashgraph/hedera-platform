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
package com.swirlds.platform.chatter.protocol.processing;

import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.Clearable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Stores event processing times for self and peers. */
public class ProcessingTimes implements Clearable {

    private final AtomicBoolean processingTimeAvailable = new AtomicBoolean();

    /** this node's event processing time */
    private final DurationGauge processingTime;

    /** latest reported processing time for each peer, keyed by peer id */
    final Map<Long, Long> peerProcessingTimes = new ConcurrentHashMap<>();

    public ProcessingTimes(final Metrics metrics) {
        processingTime =
                metrics.getOrCreate(
                        new DurationGauge.Config(
                                "chatter",
                                "eventProcTime",
                                "the time it takes to process and validate an event",
                                ChronoUnit.MILLIS));
        clear();
    }

    /**
     * Returns the current value of this node's event processing time in nanoseconds.
     *
     * @return event processing time in nanoseconds, or null if no event has been processed yet
     */
    public Long getSelfProcessingTime() {
        if (!processingTimeAvailable.get()) {
            return null;
        }
        return processingTime.getNanos();
    }

    /**
     * Records a new value for this node's event processing time
     *
     * @param newValue the new event processing time value
     */
    public void recordSelfProcessingTime(final Duration newValue) {
        processingTime.update(newValue);
        processingTimeAvailable.set(true);
    }

    public void setPeerProcessingTime(final long peerId, final long processingTime) {
        peerProcessingTimes.put(peerId, processingTime);
    }

    public Long getPeerProcessingTime(final long peerId) {
        return peerProcessingTimes.get(peerId);
    }

    @Override
    public void clear() {
        peerProcessingTimes.clear();
        processingTimeAvailable.set(false);
        processingTime.reset();
    }
}

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
package com.swirlds.platform.event;

import java.util.concurrent.atomic.AtomicLong;

/** A class that tracks the number of events in memory */
public class EventCounter {
    /**
     * Number of events currently in memory, for all instantiated Platforms put together. This is
     * only used for an internal statistic, not for any of the algorithms.
     */
    private static final AtomicLong numEventsInMemory = new AtomicLong(0);

    /**
     * Number of events currently in memory, for all instantiated Platforms put together.
     *
     * @return long value for number of events.
     */
    public static long getNumEventsInMemory() {
        return numEventsInMemory.get();
    }

    /** Called when an event is created */
    public static void eventCreated() {
        numEventsInMemory.incrementAndGet();
    }

    /**
     * Called when an event is cleared, to decrement the count of how many uncleared events are in
     * memory
     */
    public static void eventCleared() {
        numEventsInMemory.decrementAndGet();
    }
}

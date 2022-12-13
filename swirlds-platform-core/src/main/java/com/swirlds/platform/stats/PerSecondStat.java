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
package com.swirlds.platform.stats;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates a value and expects {@link #update()} to be called on it once a second in order to
 * track a per second average
 */
public class PerSecondStat {
    final AtomicLong accumulator;
    final AverageStat average;

    public PerSecondStat(final AverageStat average) {
        this.accumulator = new AtomicLong(0);
        this.average = average;
    }

    public void increment() {
        accumulator.incrementAndGet();
    }

    public void update() {
        average.update(accumulator.getAndSet(0));
    }
}

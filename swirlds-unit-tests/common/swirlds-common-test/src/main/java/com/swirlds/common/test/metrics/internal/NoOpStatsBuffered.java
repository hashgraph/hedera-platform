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
package com.swirlds.common.test.metrics.internal;

import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.internal.StatsBuffer;

/** A no-op implementation of {@link StatsBuffered}. */
public class NoOpStatsBuffered implements StatsBuffered {

    /** {@inheritDoc} */
    @Override
    public StatsBuffer getAllHistory() {
        return new StatsBuffer(0, 0, 0);
    }

    /** {@inheritDoc} */
    @Override
    public StatsBuffer getRecentHistory() {
        return new StatsBuffer(0, 0, 0);
    }

    /** {@inheritDoc} */
    @Override
    public void reset(final double halflife) {}

    /** {@inheritDoc} */
    @Override
    public double getMean() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double getMax() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double getMin() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public double getStdDev() {
        return 0;
    }
}

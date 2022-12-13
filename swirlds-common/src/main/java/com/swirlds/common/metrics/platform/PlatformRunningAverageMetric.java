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
package com.swirlds.common.metrics.platform;

import com.swirlds.common.Clock;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.StatsRunningAverage;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Platform-implementation of {@link RunningAverageMetric} */
@SuppressWarnings("unused")
public class PlatformRunningAverageMetric extends AbstractDistributionMetric
        implements RunningAverageMetric {

    @SuppressWarnings("removal")
    private final StatsRunningAverage runningAverage;

    public PlatformRunningAverageMetric(final RunningAverageMetric.Config config) {
        this(config, Clock.DEFAULT);
    }

    /** This constructor should only be used for testing. */
    @SuppressWarnings("removal")
    public PlatformRunningAverageMetric(
            final RunningAverageMetric.Config config, final Clock clock) {
        super(config, config.getHalfLife());
        this.runningAverage = new StatsRunningAverage(halfLife, clock);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("removal")
    @Override
    public StatsBuffered getStatsBuffered() {
        return runningAverage;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("removal")
    @Override
    public void update(final double value) {
        runningAverage.recordValue(value);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("removal")
    @Override
    public double get() {
        return runningAverage.getWeightedMean();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("halfLife", halfLife)
                .append("value", get())
                .toString();
    }
}

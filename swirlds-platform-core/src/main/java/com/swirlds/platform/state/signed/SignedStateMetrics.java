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
package com.swirlds.platform.state.signed;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_15_3;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_16_2;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.metrics.SpeedometerMetric;

/** Encapsulates various signed state metrics. */
public class SignedStateMetrics {

    private static final String CATEGORY = "platform";

    private static final RunningAverageMetric.Config FRESH_STATES_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "freshUnsignedStates")
                    .withDescription("Average Number Of Fresh States Awaiting Signatures")
                    .withFormat(FORMAT_10_2);
    private final RunningAverageMetric freshStates;

    private static final RunningAverageMetric.Config STALE_STATES_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "staleUnsignedStates")
                    .withDescription("Average Number Of Stale States Awaiting Signatures")
                    .withFormat(FORMAT_10_2);
    private final RunningAverageMetric staleStates;

    private static final RunningAverageMetric.Config AVERAGE_SIGNING_TIME_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "averageStateSigningTime")
                    .withDescription("The average time required to sign a state in milliseconds")
                    .withFormat(FORMAT_10_2);
    private final RunningAverageMetric averageSigningTime;

    private static final Counter.Config TOTAL_NEVER_SIGNED_STATES_CONFIG =
            new Counter.Config(CATEGORY, "totalNeverSignedStates")
                    .withDescription(
                            "total number of states that did not receive enough signatures in the "
                                    + "allowed time");
    private final Counter totalNeverSignedStates;

    private static final Counter.Config TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG =
            new Counter.Config(CATEGORY, "totalNeverSignedDiskStates")
                    .withDescription(
                            "total number of disk-bound states that did not receive enough"
                                    + " signatures in the allowed time");
    private final Counter totalNeverSignedDiskStates;

    private static final SpeedometerMetric.Config STATES_SIGNED_PER_SECOND_CONFIG =
            new SpeedometerMetric.Config(CATEGORY, "statesSigned/sec")
                    .withDescription("the number of states completely signed per second")
                    .withFormat(FORMAT_16_2);
    private final SpeedometerMetric statesSignedPerSecond;

    private static final SpeedometerMetric.Config STATE_SIGNATURES_GATHERED_PER_SECOND_CONFIG =
            new SpeedometerMetric.Config(CATEGORY, "stateSignaturesGathered/sec")
                    .withDescription(
                            "the number of state signatures gathered from other nodes per second")
                    .withFormat(FORMAT_16_2);
    private final SpeedometerMetric stateSignaturesGatheredPerSecond;

    private static final RunningAverageMetric.Config STATE_ARCHIVAL_TIME_AVG_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "stateArchivalTimeAvg")
                    .withDescription("avg time to archive a signed state (in milliseconds)")
                    .withFormat(FORMAT_15_3);
    private final RunningAverageMetric stateArchivalTimeAvg;

    private static final RunningAverageMetric.Config STATE_DELETION_QUEUE_AVG_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "stateDeletionQueueAvg")
                    .withDescription("avg length of the state deletion queue")
                    .withFormat(FORMAT_15_3);
    private final RunningAverageMetric stateDeletionQueueAvg;

    private static final RunningAverageMetric.Config STATE_DELETION_TIME_AVG_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "stateDeletionTimeAvg")
                    .withDescription("avg time it takes to delete a signed state (in milliseconds)")
                    .withFormat(FORMAT_15_3);
    private final RunningAverageMetric stateDeletionTimeAvg;

    private static final RunningAverageMetric.Config STATE_HASHING_TIME_CONFIG =
            new RunningAverageMetric.Config(CATEGORY, "sigStateHash")
                    .withDescription(
                            "average time it takes to hash a SignedState (in milliseconds)")
                    .withFormat(FORMAT_10_3);
    private final RunningAverageMetric stateHashingTime;

    /** Get a metric tracking fresh unsigned states. */
    public RunningAverageMetric getFreshStatesMetric() {
        return freshStates;
    }

    /** Get a metric tracking stale unsigned states. */
    public RunningAverageMetric getStaleStatesMetric() {
        return staleStates;
    }

    /** Get a metric tracking average state signing time in milliseconds. */
    public RunningAverageMetric getAverageSigningTimeMetric() {
        return averageSigningTime;
    }

    /** Get a metric tracking the total number of unsigned states that were skipped. */
    public Counter getTotalUnsignedStatesMetric() {
        return totalNeverSignedStates;
    }

    /**
     * Get a metric tracking the total number of unsigned states written to disk that were skipped.
     */
    public Counter getTotalUnsignedDiskStatesMetric() {
        return totalNeverSignedDiskStates;
    }

    /** Get a metric tracking the total number of states signed per second. */
    public SpeedometerMetric getStatesSignedPerSecondMetric() {
        return statesSignedPerSecond;
    }

    /**
     * Get a metric tracking the total number of state signatures gathered from other nodes per
     * second.
     */
    public SpeedometerMetric getStateSignaturesGatheredPerSecondMetric() {
        return stateSignaturesGatheredPerSecond;
    }

    /** Get a metric tracking the average time required to archive a state. */
    public RunningAverageMetric getStateArchivalTimeAvgMetric() {
        return stateArchivalTimeAvg;
    }

    /** Get a metric tracking the average size of the signed state deletion/archive queue. */
    public RunningAverageMetric getStateDeletionQueueAvgMetric() {
        return stateDeletionQueueAvg;
    }

    /** Get a metric tracking the average time to delete a signed state. */
    public RunningAverageMetric getStateDeletionTimeAvgMetric() {
        return stateDeletionTimeAvg;
    }

    /** Get a metric tracking the average time required to hash a state. */
    public RunningAverageMetric getSignedStateHashingTimeMetric() {
        return stateHashingTime;
    }

    /**
     * Register all metrics with a registry.
     *
     * @param metrics a reference to the metrics-system
     */
    public SignedStateMetrics(final Metrics metrics) {
        freshStates = metrics.getOrCreate(FRESH_STATES_CONFIG);
        staleStates = metrics.getOrCreate(STALE_STATES_CONFIG);
        averageSigningTime = metrics.getOrCreate(AVERAGE_SIGNING_TIME_CONFIG);
        totalNeverSignedStates = metrics.getOrCreate(TOTAL_NEVER_SIGNED_STATES_CONFIG);
        totalNeverSignedDiskStates = metrics.getOrCreate(TOTAL_NEVER_SIGNED_DISK_STATES_CONFIG);
        statesSignedPerSecond = metrics.getOrCreate(STATES_SIGNED_PER_SECOND_CONFIG);
        stateSignaturesGatheredPerSecond =
                metrics.getOrCreate(STATE_SIGNATURES_GATHERED_PER_SECOND_CONFIG);
        stateArchivalTimeAvg = metrics.getOrCreate(STATE_ARCHIVAL_TIME_AVG_CONFIG);
        stateDeletionQueueAvg = metrics.getOrCreate(STATE_DELETION_QUEUE_AVG_CONFIG);
        stateDeletionTimeAvg = metrics.getOrCreate(STATE_DELETION_TIME_AVG_CONFIG);
        stateHashingTime = metrics.getOrCreate(STATE_HASHING_TIME_CONFIG);
    }
}

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
package com.swirlds.platform.metrics;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_8_1;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import com.swirlds.platform.stats.CycleTimingStat;
import com.swirlds.platform.stats.cycle.CycleDefinition;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/** Provides access to statistics relevant to {@link ConsensusRoundHandler} */
public class ConsensusHandlingMetrics {
    private final CycleTimingStat consensusCycleTiming;

    private final CycleTimingStat newSignedStateCycleTiming;

    private final AverageAndMax avgEventsPerRound;

    /**
     * Constructor of {@code ConsensusHandlingMetrics}
     *
     * @param metrics a reference to the metrics-system
     * @throws IllegalArgumentException if {@code metrics} is {@code null}
     */
    public ConsensusHandlingMetrics(final Metrics metrics) {
        CommonUtils.throwArgNull(metrics, "metrics");
        consensusCycleTiming =
                new CycleTimingStat(
                        metrics,
                        ChronoUnit.MILLIS,
                        new CycleDefinition(
                                INTERNAL_CATEGORY,
                                "consRound",
                                List.of(
                                        Pair.of(
                                                "dataPropMillis/round",
                                                "average time to propagate consensus data to"
                                                        + " transactions"),
                                        Pair.of(
                                                "handleMillis/round",
                                                "average time to handle a consensus round"),
                                        Pair.of(
                                                "roundCompletedDispatch/round",
                                                "average time to send round completed dispatch"),
                                        Pair.of(
                                                "storeMillis/round",
                                                "average time to add consensus round events to"
                                                        + " signed state storage"),
                                        Pair.of(
                                                "hashMillis/round",
                                                "average time spent hashing the consensus round"
                                                        + " events"),
                                        Pair.of(
                                                "buildStateMillis",
                                                "average time spent building a signed state"),
                                        Pair.of(
                                                "forSigCleanMillis",
                                                "average time spent expiring signed state storage"
                                                        + " events"))));
        newSignedStateCycleTiming =
                new CycleTimingStat(
                        metrics,
                        ChronoUnit.MICROS,
                        new CycleDefinition(
                                INTERNAL_CATEGORY,
                                "newSS",
                                List.of(
                                        Pair.of(
                                                "getStateMicros",
                                                "average time to get the state to sign"),
                                        Pair.of(
                                                "getStateDataMicros",
                                                "average time to get events and min gen info"),
                                        Pair.of(
                                                "runningHashMicros",
                                                "average time spent waiting on the running hash"
                                                        + " future"),
                                        Pair.of(
                                                "newSSInstanceMicros",
                                                "average time spent creating the new signed state"
                                                        + " instance"),
                                        Pair.of(
                                                "queueAdmitMicros",
                                                "average time spent admitting the signed state to"
                                                        + " the signing queue"))));
        avgEventsPerRound =
                new AverageAndMax(
                        metrics,
                        INTERNAL_CATEGORY,
                        "events/round",
                        "average number of events in a consensus round",
                        FORMAT_8_1,
                        AverageStat.WEIGHT_VOLATILE);
    }

    /**
     * @return the cycle timing stat that keeps track of how much time is spent in various parts of
     *     {@link ConsensusRoundHandler#consensusRound(ConsensusRound)}
     */
    public CycleTimingStat getConsCycleStat() {
        return consensusCycleTiming;
    }

    /**
     * @return the cycle timing stat that keeps track of how much time is spent creating a new
     *     signed state in {@link ConsensusRoundHandler#consensusRound(ConsensusRound)}
     */
    public CycleTimingStat getNewSignedStateCycleStat() {
        return newSignedStateCycleTiming;
    }

    /**
     * Records the number of events in a round.
     *
     * @param numEvents the number of events in the round
     */
    public void recordEventsPerRound(final int numEvents) {
        avgEventsPerRound.update(numEvents);
    }
}

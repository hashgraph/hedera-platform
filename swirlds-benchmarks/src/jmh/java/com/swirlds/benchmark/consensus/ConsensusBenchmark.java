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
package com.swirlds.benchmark.consensus;

import com.swirlds.common.test.StakeGenerators;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.test.NoOpConsensusMetrics;
import com.swirlds.platform.test.consensus.ConsensusTestDefinition;
import com.swirlds.platform.test.event.IndexedEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 3)
public class ConsensusBenchmark {
    @Param({"39"})
    public int numNodes;

    @Param({"10000"})
    public int numEvents;

    @Param({"0"})
    public long seed;

    private List<IndexedEvent> events;
    private Consensus consensus;

    @Setup
    public void setup() {
        final ConsensusTestDefinition testDefinition =
                new ConsensusTestDefinition(
                        "Performance Test",
                        numNodes,
                        (l, i) -> StakeGenerators.balancedNodeStakes(i),
                        numEvents);
        testDefinition.setSeed(seed);
        events = testDefinition.getNode1EventEmitter().emitEvents(numEvents);
        consensus =
                new ConsensusImpl(
                        new NoOpConsensusMetrics(),
                        (r, g) -> {},
                        testDefinition.getNode1EventEmitter().getGraphGenerator().getAddressBook());
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void calculateConsensus(final Blackhole bh) {
        for (final IndexedEvent event : events) {
            bh.consume(consensus.addEvent(event, null));
        }
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt =
                new OptionsBuilder()
                        .include(ConsensusBenchmark.class.getSimpleName())
                        .warmupIterations(1)
                        .measurementIterations(2)
                        .warmupTime(TimeValue.seconds(1))
                        .measurementTime(TimeValue.seconds(10))
                        .forks(1)
                        .build();

        final Collection<RunResult> run = new Runner(opt).run();

        final List<Pair<String, Double>> resultComparison =
                List.of(
                        Pair.of("Dell Precision 5540", 105.0),
                        Pair.of("M1 Max MacBook Pro (2021)", 75.0));
        final double actualScore =
                run.stream().findFirst().orElseThrow().getPrimaryResult().getScore();

        for (final Pair<String, Double> pair : resultComparison) {
            final double diff = actualScore - pair.getRight();
            System.out.printf(
                    "Compared to '%s': %+.2f%%%n", pair.getLeft(), (100 * diff) / pair.getRight());
        }
    }
}

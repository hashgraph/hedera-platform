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
package com.swirlds.platform.test.recovery;

import static com.swirlds.platform.test.consensus.ConsensusUtils.buildSimpleConsensus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.recovery.RecoveredEventAggregator;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.event.emitter.EventEmitterFactory;
import com.swirlds.platform.test.event.emitter.StandardEventEmitter;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecoveredEventAggregatorTests {

    private static final int NUM_NODES = 4;
    private AddressBook addressBook;

    private static Stream<Arguments> roundNums() {
        return Stream.of(
                Arguments.of(1, true),
                Arguments.of(2, true),
                Arguments.of(10, true),
                Arguments.of(2, false),
                Arguments.of(10, false));
    }

    @ParameterizedTest
    @MethodSource("roundNums")
    void testCompleteRounds(final int roundsToSend, final boolean sendCompleteRound) {
        final Random random = RandomUtils.initRandom(null);
        addressBook =
                new RandomAddressBookGenerator(random)
                        .setSize(NUM_NODES)
                        .setSequentialIds(true)
                        .build();

        final List<ConsensusRound> roundReceived = new LinkedList<>();
        final List<Pair<Long, Long>> minGens = new LinkedList<>();

        final RecoveredEventAggregator aggregator =
                new RecoveredEventAggregator(
                        roundReceived::add, (v1, v2) -> minGens.add(Pair.of(v1, v2)));

        final List<ConsensusRound> roundsSent =
                sendRounds(random, aggregator, roundsToSend, sendCompleteRound);

        aggregator.noMoreEvents();
        aggregator.sendLastRound();

        // verify test integrity
        assertEquals(
                roundsToSend,
                roundsSent.size(),
                "Incorrect number of rounds sent to the aggregator");

        // verify code under test
        assertEquals(
                roundsToSend,
                roundReceived.size(),
                "Incorrect number of rounds forwarded to the round consumer");
        assertEquals(
                roundsToSend,
                minGens.size(),
                "Incorrect number of min gens forwarded to the min gen consumer");

        for (int i = 0; i < roundReceived.size(); i++) {
            assertRoundIntegrity(roundReceived.get(i), roundsSent.get(i), minGens.get(i));
        }

        final long expectedEventsSent =
                roundsSent.stream().mapToLong(x -> x.getConsensusEvents().size()).sum();
        final long expectedLastCompleteRound;
        if (sendCompleteRound) {
            expectedLastCompleteRound = roundsSent.get(roundsSent.size() - 1).getRoundNum();
        } else {
            expectedLastCompleteRound = roundsSent.get(roundsSent.size() - 2).getRoundNum();
        }

        assertEquals(
                expectedLastCompleteRound,
                aggregator.getLastCompleteRound(),
                "Incorrect last complete round");
        assertEquals(roundsToSend, aggregator.getTotalRounds(), "Incorrect number of rounds sent");
        assertEquals(
                expectedEventsSent, aggregator.getTotalEvents(), "Incorrect number of rounds sent");
    }

    private List<ConsensusRound> sendRounds(
            final Random random,
            final RecoveredEventAggregator aggregator,
            final int numRoundsToSend,
            final boolean completeRounds) {

        final StandardEventEmitter emitter =
                new EventEmitterFactory(random, NUM_NODES).newStandardEmitter();
        final Consensus consensus = buildSimpleConsensus(addressBook);
        final List<ConsensusRound> consensusRounds = new LinkedList<>();

        while (consensusRounds.size() < numRoundsToSend) {
            consensusRounds.addAll(
                    ConsensusUtils.applyEventsToConsensusUsingWrapper(emitter, consensus, 1));
        }

        final int numRoundsGenerated = consensusRounds.size();
        final int numExtraGenerated = numRoundsGenerated - numRoundsToSend;
        final List<ConsensusRound> roundsToSend =
                consensusRounds.subList(numExtraGenerated, numRoundsGenerated);
        for (final ConsensusRound round : roundsToSend) {
            for (final EventImpl e : round.getConsensusEvents()) {
                aggregator.addEvent(e);
            }
        }

        if (!completeRounds) {
            final ConsensusRound lastRound = consensusRounds.get(consensusRounds.size() - 1);
            lastRound.getLastEvent().setLastInRoundReceived(false);
        }

        return roundsToSend;
    }

    private void assertRoundIntegrity(
            final ConsensusRound roundReceived,
            final ConsensusRound roundSent,
            final Pair<Long, Long> minGenPair) {
        assertEquals(roundReceived.getRoundNum(), minGenPair.getLeft(), "Mismatched min gen round");
        final long roundNum = roundReceived.getRoundNum();
        long minGen = Long.MAX_VALUE;
        Instant prevConsensusTime = null;
        for (final EventImpl event : roundReceived.getConsensusEvents()) {
            assertTrue(
                    roundSent.getConsensusEvents().contains(event),
                    String.format(
                            "Event received in round %s was not sent in that round",
                            event.getRoundReceived()));
            minGen = Math.min(minGen, event.getGeneration());
            assertEquals(
                    roundNum,
                    event.getRoundReceived(),
                    String.format(
                            "Round has events from different rounds (roundNum %s contains event in"
                                    + " round %s)",
                            roundNum, event.getRoundReceived()));
            if (prevConsensusTime != null) {
                assertTrue(
                        prevConsensusTime.isBefore(event.getConsensusTimestamp()),
                        "Events in round are out of consensus order");
            }
            prevConsensusTime = event.getConsensusTimestamp();
        }
        assertEquals(
                minGen,
                minGenPair.getRight(),
                "Incorrect min gen for round " + roundReceived.getRoundNum());
    }
}

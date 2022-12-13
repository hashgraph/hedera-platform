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
package com.swirlds.platform.test.consensus;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.Settings;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.state.StateSettings;
import com.swirlds.platform.test.event.DynamicValue;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.generator.GraphGenerator;
import com.swirlds.platform.test.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.event.source.EventSource;
import com.swirlds.platform.test.event.source.StandardEventSource;
import com.swirlds.platform.test.graph.OtherParentMatrixFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class IntakeAndConsensusTests {
    @AfterAll
    public static void afterAll() {
        // revert the settings so that it does not affect other tests
        final StateSettings defaultSettings = new StateSettings();
        Settings.getInstance().getState().roundsNonAncient = defaultSettings.roundsNonAncient;
        Settings.getInstance().getState().roundsExpired = defaultSettings.roundsExpired;
    }

    /**
     * Reproduces #5635
     *
     * <p>This test creates a graph with two partitions, where one partition is small enough that it
     * is not needed for consensus. Because the small partition does not affect consensus, we can
     * delay inserting those events and still reach consensus. We delay adding the small partition
     * events until the first of these events becomes ancient. This would lead to at least one
     * subsequent small partition event being non-ancient, but not having only ancient parents. We
     * then insert the small partition events into 2 Node objects that have different consensus
     * states. In one node, these small partition parents are ancient, in the other they are not. We
     * then stop partitioning, so that new events will be descendants of some small partition
     * events. This means that the small partition events will now be needed for consensus. If the
     * small partition events are not inserted into one of the nodes correctly, it will not be able
     * to reach consensus.
     *
     * <p>Tests the workaround described in #5762
     */
    @Test
    @Tag(TIME_CONSUMING)
    void nonAncientEventWithMissingParents() {
        final long seed = 0;
        final int numNodes = 10;
        final List<Integer> partitionNodes = List.of(0, 1);
        Settings.getInstance().getState().roundsNonAncient = 25;
        Settings.getInstance().getState().roundsExpired = 24;
        // roundsExpired seems to have an off-by-one error, so it's lower than roundsNonAncient, but
        // the effect is as
        // though they are the same

        // the generated events are first fed into consensus so that round created is calculated
        // before we start
        // using them
        final GeneratorWithConsensus generator = new GeneratorWithConsensus(seed, numNodes);
        final TestIntake node1 = new TestIntake(generator.getAddressBook());
        final TestIntake node2 = new TestIntake(generator.getAddressBook());

        // first we generate events regularly, until we have some ancient rounds
        final int firstBatchSize = 5000;
        List<IndexedEvent> batch = generator.generateEvents(firstBatchSize);
        for (final IndexedEvent event : batch) {
            node1.addEvent(event.getBaseEvent());
            node2.addEvent(event.getBaseEvent());
        }

        // System.out.printf("after the first batch of %d events:\n", firstBatchSize);
        printGenerations(node1, 1);
        printGenerations(node2, 2);

        assertConsensusEvents(node1, node2);

        // now we create a partition
        generator.setOtherParentAffinity(
                OtherParentMatrixFactory.createPartitionedOtherParentAffinityMatrix(
                        numNodes, partitionNodes));

        // during the partition, we will not insert the minority partition events into consensus
        // we generate just enough events to make the first event of the partition ancient, but we
        // don't insert the
        // last event into the second consensus
        long partitionMinGen = EventConstants.GENERATION_UNDEFINED;
        long partitionMaxGen = EventConstants.GENERATION_UNDEFINED;
        final List<IndexedEvent> partitionedEvents = new LinkedList<>();
        boolean succeeded = false;
        IndexedEvent lastEvent = null;
        while (!succeeded) {
            batch = generator.generateEvents(1);
            lastEvent = batch.get(0);
            if (partitionNodes.contains((int) lastEvent.getCreatorId())) {
                partitionMinGen =
                        partitionMinGen == EventConstants.GENERATION_UNDEFINED
                                ? lastEvent.getGeneration()
                                : Math.min(partitionMinGen, lastEvent.getGeneration());
                partitionMaxGen = Math.max(partitionMaxGen, lastEvent.getGeneration());
                partitionedEvents.add(lastEvent);
            } else {
                node1.addEvent(lastEvent.getBaseEvent());
                final long node1NonAncGen = node1.getConsensus().getMinGenerationNonAncient();
                if (partitionMaxGen > node1NonAncGen && partitionMinGen < node1NonAncGen) {
                    succeeded = true;
                } else {
                    node2.addEvent(lastEvent.getBaseEvent());
                }
            }
        }

        // System.out.println("after the partition and mini batches:");
        printGenerations(node1, 1);
        printGenerations(node2, 2);
        // System.out.printf("- partitionMinGen:%d partitionMaxGen:%d\n", partitionMinGen,
        // partitionMaxGen);

        // now we insert the minority partition events into both consensus objects, which are in a
        // different state of
        // consensus
        node1.addEvents(partitionedEvents);
        node2.addEvents(partitionedEvents);
        // now we add the event that was added to 1 but not to 2
        node2.addEvent(lastEvent.getBaseEvent());
        assertConsensusEvents(node1, node2);

        // System.out.println("after adding partition events and last mini batch to node 2:");
        printGenerations(node1, 1);
        printGenerations(node2, 2);
        // System.out.printf("- partitionMinGen:%d partitionMaxGen:%d\n", partitionMinGen,
        // partitionMaxGen);

        // now the partitions rejoin
        generator.setOtherParentAffinity(
                OtherParentMatrixFactory.createBalancedOtherParentMatrix(numNodes));

        // now we generate more events and expect consensus to be the same
        final int secondBatchSize = 1000;
        batch = generator.generateEvents(secondBatchSize);
        for (final IndexedEvent event : batch) {
            node1.addEvent(event.getBaseEvent());
            node2.addEvent(event.getBaseEvent());
        }
        // System.out.println("after the partitions rejoin:");
        printGenerations(node1, 1);
        printGenerations(node2, 2);
        assertConsensusEvents(node1, node2);
    }

    private static void assertConsensusEvents(final TestIntake node1, final TestIntake node2) {
        Assertions.assertEquals(
                node1.getConsensusRounds(),
                node2.getConsensusRounds(),
                "consensus rounds must always be equal");
        node1.getConsensusRounds().clear();
        node2.getConsensusRounds().clear();
    }

    private static void printGenerations(final TestIntake node, final int nodeNum) {
        //		System.out.printf("- node %d - minRound:%d maxRound:%d\n",
        //				nodeNum,
        //				node.getConsensus().getMinRound(),
        //				node.getConsensus().getMaxRound());
        //		System.out.printf("- node %d - minRoundGen:%d nonAncGen:%d maxRoundGen:%d\n",
        //				nodeNum,
        //				node.getConsensus().getMinRoundGeneration(),
        //				node.getConsensus().getMinGenerationNonAncient(),
        //				node.getConsensus().getMaxRoundGeneration());
    }

    private static class GeneratorWithConsensus implements GraphGenerator<GeneratorWithConsensus> {
        private final StandardGraphGenerator generator;
        private final TestIntake intake;

        @SuppressWarnings("unchecked")
        public GeneratorWithConsensus(final long seed, final int numNodes) {
            final List<StandardEventSource> eventSources =
                    Stream.generate(StandardEventSource::new).limit(numNodes).toList();
            generator =
                    new StandardGraphGenerator(seed, (List<EventSource<?>>) (List<?>) eventSources);
            intake = new TestIntake(generator.getAddressBook());
        }

        @Override
        public IndexedEvent generateEvent() {
            final IndexedEvent event = generator.generateEvent();
            intake.addEvent(event);
            return event;
        }

        @Override
        public int getNumberOfSources() {
            return generator.getNumberOfSources();
        }

        @Override
        public EventSource<?> getSource(final int nodeID) {
            return generator.getSource(nodeID);
        }

        @Override
        public GeneratorWithConsensus cleanCopy() {
            throw new UnsupportedOperationException("not implements");
        }

        @Override
        public GeneratorWithConsensus cleanCopy(final long seed) {
            throw new UnsupportedOperationException("not implements");
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException("not implements");
        }

        @Override
        public long getNumEventsGenerated() {
            return generator.getNumEventsGenerated();
        }

        @Override
        public AddressBook getAddressBook() {
            return generator.getAddressBook();
        }

        @Override
        public long getMaxGeneration(final long creatorId) {
            return generator.getMaxGeneration(creatorId);
        }

        @Override
        public long getMaxGeneration() {
            return generator.getMaxGeneration();
        }

        @Override
        public void setOtherParentAffinity(final List<List<Double>> affinityMatrix) {
            generator.setOtherParentAffinity(affinityMatrix);
        }

        @Override
        public void setOtherParentAffinity(final DynamicValue<List<List<Double>>> affinityMatrix) {
            generator.setOtherParentAffinity(affinityMatrix);
        }
    }
}

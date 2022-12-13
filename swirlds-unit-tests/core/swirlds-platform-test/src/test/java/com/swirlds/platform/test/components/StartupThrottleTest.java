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
package com.swirlds.platform.test.components;

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse.SYNC_AND_CREATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.components.PlatformStatusManager;
import com.swirlds.platform.components.StartupThrottle;
import com.swirlds.platform.components.TransThrottleSyncAndCreateRuleResponse;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class StartupThrottleTest {
    private static final int NUMBER_OF_NODES = 10;

    private static final PlatformStatusManager NOOP_STATUS_MANAGER = () -> {};

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("tests startup throttle")
    void basicTest() {
        final StartupThrottle[] throttles = initializeStartupThrottles(NUMBER_OF_NODES);

        // nobody should create events before node 0
        assertEquals(PASS, throttles[0].shouldCreateEvent(), "expected not to be throttled");
        for (int i = 1; i < NUMBER_OF_NODES; i++) {
            assertEquals(
                    DONT_CREATE,
                    throttles[i].shouldCreateEvent(),
                    "only 0 should create events at the start");
        }

        // 0 now creates an event
        startNode(0, throttles);

        // everybody should be to create one event except for 0
        for (int i = 1; i < NUMBER_OF_NODES - 1; i++) {
            assertEquals(PASS, throttles[i].shouldCreateEvent(), "expected not to be throttled");
        }

        // now all nodes start except the last one
        for (int i = 1; i < NUMBER_OF_NODES - 1; i++) {
            startNode(i, throttles);
        }

        // now no one should create an event except for the last node
        for (int i = 0; i < NUMBER_OF_NODES - 1; i++) {
            assertEquals(DONT_CREATE, throttles[i].shouldCreateEvent(), "expected to be throttled");
        }
        assertEquals(
                PASS,
                throttles[NUMBER_OF_NODES - 1].shouldCreateEvent(),
                "expected not to be throttled");

        // now we start the last node
        startNode(NUMBER_OF_NODES - 1, throttles);

        // now everybody should be able to create events
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            assertEquals(PASS, throttles[i].shouldCreateEvent(), "expected not to be throttled");
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("NoOp startup throttle test")
    void testNoop() {
        final int numberOfNodes = 10;
        StartupThrottle throttle = StartupThrottle.getNoOpInstance();
        for (int i = 0; i < numberOfNodes; i++) {
            assertEquals(PASS, throttle.shouldCreateEvent(), "expected not to be throttled");
        }
        assertTrue(throttle.allNodesStarted(), "expected not to be throttled");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("shouldWaitForNode0 test")
    void shouldWaitForNode0Test() {
        final StartupThrottle[] throttles = initializeStartupThrottles(NUMBER_OF_NODES);

        assertFalse(
                throttles[0].shouldWaitForNode0(),
                "shouldWaitForNode0() should return false for node0");
        for (int i = 1; i < NUMBER_OF_NODES; i++) {
            assertTrue(
                    throttles[i].shouldWaitForNode0(),
                    "shouldWaitForNode0() should return true for nodes except node0 when node0"
                            + " hasn't started up");
        }

        // 0 creates an event
        startNode(0, throttles);

        for (int i = 1; i < NUMBER_OF_NODES; i++) {
            assertFalse(
                    throttles[i].shouldWaitForNode0(),
                    "shouldWaitForNode0() should return false when node0 has started up");
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("shouldWaitForNode0 test")
    void shouldTransThrottleTest() {
        final StartupThrottle[] throttles = initializeStartupThrottles(NUMBER_OF_NODES);

        assertFalse(
                throttles[0].shouldWaitForNode0(),
                "shouldWaitForNode0() should return false for node0");
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            assertEquals(
                    SYNC_AND_CREATE,
                    throttles[i].shouldSyncAndCreate(),
                    "should not throttle when all nodes hasn't started up");
        }

        for (int i = 0; i < NUMBER_OF_NODES - 1; i++) {
            startNode(i, throttles);
            assertEquals(
                    SYNC_AND_CREATE,
                    throttles[i].shouldSyncAndCreate(),
                    "should not throttle when all nodes hasn't started up");
        }

        startNode(NUMBER_OF_NODES - 1, throttles);
        for (int i = 0; i < NUMBER_OF_NODES; i++) {
            assertEquals(
                    TransThrottleSyncAndCreateRuleResponse.PASS,
                    throttles[i].shouldSyncAndCreate(),
                    "should pass when all nodes have started up");
        }
    }

    /**
     * start the node with given nodeId
     *
     * @param nodeId id of the node to start
     * @param throttles an array of StartupThrottle
     */
    private void startNode(final long nodeId, final StartupThrottle[] throttles) {
        for (int i = 0; i < throttles.length; i++) {
            throttles[i].nodeStarted(nodeId);
        }
    }

    /**
     * inits an array of StartupThrottles with given size
     *
     * @param numberOfNodes number of nodes in AddressBook
     * @return an array of StartupThrottles with given size
     */
    private StartupThrottle[] initializeStartupThrottles(final int numberOfNodes) {
        final boolean betaMirrorNodes = false;
        AddressBook addressBook =
                new RandomAddressBookGenerator()
                        .setSize(numberOfNodes)
                        .setStakeDistributionStrategy(
                                RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
                        .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                        .build();

        StartupThrottle[] throttles = new StartupThrottle[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            throttles[i] =
                    new StartupThrottle(
                            addressBook,
                            NodeId.createMain(i),
                            NOOP_STATUS_MANAGER,
                            betaMirrorNodes);
        }
        return throttles;
    }
}

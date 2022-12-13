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
package com.swirlds.platform.test.eventflow;

import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the flow of transactions and events through the system for {@link SwirldState2}
 * applications
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SwirldState2FlowTests extends EventFlowTests {

    private SwirldState2Tracker origSwirldState;

    private static Stream<Arguments> preConsParams() {
        return Stream.of(
                Arguments.of(null, 4, 1_000, new SwirldState2Tracker(selfId)),
                Arguments.of(null, 4, 10_000, new SwirldState2Tracker(selfId)));
    }

    @BeforeEach
    void setup() {
        origSwirldState = new SwirldState2Tracker();
    }

    @Override
    @AfterEach
    void cleanup() {
        origSwirldState.getPreHandleTransactions().clear();
        preConsensusEventHandler.stop();
        consensusEventHandler.stop();
    }

    /**
     * @see #testPreHandle(Long, int, SwirldState, Function)
     */
    @Order(1)
    @ParameterizedTest
    @MethodSource({"preConsParams"})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("All transactions sent to preHandle")
    void testPreHandle(final Long seed, final int numNodes, final int numTransactions)
            throws InterruptedException, ExecutionException, TimeoutException {
        testPreHandle(
                seed,
                numNodes,
                origSwirldState.waitForMetadata(true),
                (w) -> w.applyPreConsensusEvents(numTransactions, (event) -> true));
        verifyNoStateFailures();
    }

    /**
     * @see #testPostConsensusHandle(Long, int, int, SwirldState)
     */
    @Order(2)
    @ParameterizedTest
    @MethodSource("postConsHandleParams")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Transactions handled post-consensus")
    void testPostConsensusHandle(final Long seed, final int numNodes, final int numEvents)
            throws InterruptedException {
        testPostConsensusHandle(seed, numNodes, numEvents, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testSignedStateSettings(Long, int, int, int, SwirldState)
     */
    @Order(3)
    @ParameterizedTest
    @MethodSource("signedStateParams")
    @DisplayName("Signed states created for the correct rounds")
    void testSignedStateSettings(
            final Long seed, final int numNodes, final int numEvents, final int signedStateFreq)
            throws InterruptedException {
        testSignedStateSettings(seed, numNodes, numEvents, signedStateFreq, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testSignedStateFreezePeriod(Long, int, int, int, int, SwirldState)
     */
    @Order(4)
    @ParameterizedTest
    @MethodSource("freezePeriodParams")
    @DisplayName("Signed state created for freeze periods")
    void testSignedStateFreezePeriod(
            final Long seed,
            final int numNodes,
            final int numEvents,
            final int signedStateFreq,
            final int desiredFreezeRound)
            throws InterruptedException {
        testSignedStateFreezePeriod(
                seed, numNodes, numEvents, signedStateFreq, desiredFreezeRound, origSwirldState);
        verifyNoStateFailures();
    }

    /**
     * @see #testPreConsensusSystemTransactions(Long, int, int, SwirldState)
     */
    @Order(5)
    @ParameterizedTest
    @MethodSource("sysTransParams")
    @DisplayName("System transactions are handled pre-consensus")
    void testPreConsensusSystemTransactions(
            final Long seed, final int numNodes, final int numTransactions)
            throws InterruptedException {
        testPreConsensusSystemTransactions(
                seed, numNodes, numTransactions, origSwirldState.waitForMetadata(true));
        verifyNoStateFailures();
    }

    /**
     * @see #testConsensusSystemTransactions(Long, int, int)
     */
    @Order(6)
    @ParameterizedTest
    @MethodSource("sysTransParams")
    @DisplayName("System transactions are handled post-consensus")
    void testConsensusSystemTransactions(final Long seed, final int numNodes, final int numEvents)
            throws InterruptedException, ExecutionException {
        testConsensusSystemTransactions(seed, numNodes, numEvents, origSwirldState);
        verifyNoStateFailures();
    }

    private SwirldState2Tracker getLatestImmutableState() {
        final SwirldStateManagerDouble ssm2 = (SwirldStateManagerDouble) swirldStateManager;
        return (SwirldState2Tracker) ssm2.getLatestImmutableState().getSwirldState();
    }

    private void verifyNoStateFailures() {
        verifyNoFailures(getLatestImmutableState());
        verifyNoFailures(origSwirldState);
    }
}

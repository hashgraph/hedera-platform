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

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.TransactionSubmitter;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.PreConsensusEventHandler;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.event.IndexedEvent;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Provides methods to feeding inputs to the various classes used to route events through the system
 * in support of unit tests.
 */
public class EventFlowWrapper {

    private static final Consumer<ConsensusRound> NO_OP = (e) -> {};

    private final PreConsensusEventHandler preConsensusEventHandler;
    private final ConsensusRoundHandler consensusRoundHandler;
    private final SwirldStateManager swirldStateManager;
    private final EventEmitter<?> defaultEventGenerator;

    /**
     * Creates new instances and starts the handlers.
     *
     * @param random
     * @param numNodes
     * @param preConsensusEventHandler
     * @param consensusEventHandler
     */
    public EventFlowWrapper(
            final Random random,
            final int numNodes,
            final PreConsensusEventHandler preConsensusEventHandler,
            final ConsensusRoundHandler consensusEventHandler,
            final SwirldStateManager swirldStateManager) {
        this.preConsensusEventHandler = preConsensusEventHandler;
        this.consensusRoundHandler = consensusEventHandler;
        this.swirldStateManager = swirldStateManager;
        defaultEventGenerator = EventFlowTestUtils.createEventEmitter(random, numNodes);
        preConsensusEventHandler.start();
        consensusEventHandler.start();
    }

    /**
     * Submits self-transactions to {@link
     * TransactionSubmitter#submitTransaction(SwirldTransaction)}.
     *
     * @param numTransactions the number of transactions to create and apply
     * @return the transactions applied to {@link TransactionSubmitter}
     */
    public HashSet<ConsensusTransactionImpl> submitSelfTransactions(final int numTransactions) {
        // Create some transactions
        final HashSet<ConsensusTransactionImpl> selfTransactions =
                new HashSet<>(
                        Arrays.asList(
                                TransactionUtils.incrementingSwirldTransactions(numTransactions)));
        submitSelfTransactions(selfTransactions);

        return selfTransactions;
    }

    public HashSet<ConsensusTransactionImpl> applyPreConsensusEvents(
            final int numTransactions, final EventEmitter<?> eventEmitter) {
        return applyPreConsensusEvents(numTransactions, (indexedEvent) -> true, eventEmitter);
    }

    public HashSet<ConsensusTransactionImpl> applyPreConsensusEvents(
            final int numTransactions, final Predicate<IndexedEvent> shouldApplyEvent) {
        return applyPreConsensusEvents(numTransactions, shouldApplyEvent, defaultEventGenerator);
    }

    /**
     * Generates and applies pre-consensus events to {@link
     * PreConsensusEventHandler#preConsensusEvent(EventImpl)} such that a minimum number of
     * transactions is achieved, then waits for the events to be processed. Events are only applied
     * if they pass the {@code shouldApplyEvent} check.
     *
     * @param numTransactions the minimum number of transactions in the events supplied to the
     *     {@code consumer}
     * @param shouldApplyEvent determines if an event should be applied to the {@code consumer}
     * @return the set of {@link Transaction} objects in the events applied to the {@code consumer}
     * @throws InterruptedException if this thread is interrupted
     */
    public HashSet<ConsensusTransactionImpl> applyPreConsensusEvents(
            final int numTransactions,
            final Predicate<IndexedEvent> shouldApplyEvent,
            final EventEmitter<?> eventEmitter) {
        final List<IndexedEvent> eventsToApply = new ArrayList<>();
        final HashSet<ConsensusTransactionImpl> transactions = new HashSet<>();

        while (transactions.size() < numTransactions) {
            final IndexedEvent event = eventEmitter.emitEvent();
            if (shouldApplyEvent.test(event)) {
                eventsToApply.add(event);
                for (final ConsensusTransactionImpl tx : event.getTransactions()) {
                    assertTrue(transactions.add(tx), "A duplicate transaction was generated");
                }
            }
        }

        eventsToApply.forEach(preConsensusEventHandler::preConsensusEvent);

        // Allow some time to process the events
        assertEventuallyEquals(
                0,
                preConsensusEventHandler::getQueueSize,
                Duration.ofSeconds(5),
                "Pre-consensus event queue was not drained, "
                        + preConsensusEventHandler.getQueueSize()
                        + " left");

        return transactions;
    }

    /**
     * Generates events and applies them to {@link Consensus}.
     *
     * @param addressBook the address book of the network
     * @param numEvents the number of events to apply to consensus
     * @return the events that reached consensus and were forwarded to {@link ConsensusRoundHandler}
     * @throws InterruptedException if this thread is interrupted
     * @see #applyConsensusRounds(AddressBook, int, Consumer, EventEmitter)
     */
    public List<ConsensusRound> applyConsensusRounds(
            final AddressBook addressBook, final int numEvents) throws InterruptedException {
        return applyConsensusRounds(addressBook, numEvents, NO_OP, defaultEventGenerator);
    }

    public List<ConsensusRound> applyConsensusRounds(
            final AddressBook addressBook, final int numEvents, final EventEmitter<?> eventEmitter)
            throws InterruptedException {
        return applyConsensusRounds(addressBook, numEvents, NO_OP, eventEmitter);
    }

    public List<ConsensusRound> applyConsensusRounds(
            final AddressBook addressBook,
            final int numEvents,
            final Consumer<ConsensusRound> roundConsumer)
            throws InterruptedException {
        return applyConsensusRounds(addressBook, numEvents, roundConsumer, defaultEventGenerator);
    }

    /**
     * Generates events, applies them to {@link Consensus}, then forwards the consensus rounds to
     * {@link ConsensusRoundHandler}.
     *
     * @param addressBook the address book of the network
     * @param numEvents the number of events to apply to consensus
     * @param roundConsumer a consumer for each consensus rounds immediately before it is forwarded
     *     to {@link ConsensusRoundHandler}
     * @return the events that reached consensus and were forwarded to {@link ConsensusRoundHandler}
     * @throws InterruptedException if this thread is interrupted
     */
    public List<ConsensusRound> applyConsensusRounds(
            final AddressBook addressBook,
            final int numEvents,
            final Consumer<ConsensusRound> roundConsumer,
            final EventEmitter<?> eventEmitter) {

        final Consensus consensus =
                ConsensusUtils.buildSimpleConsensus(
                        addressBook, consensusRoundHandler::addMinGenInfo);

        final List<ConsensusRound> allConsensusRounds = new LinkedList<>();

        for (int i = 0; i < numEvents; i++) {
            // Apply events to consensus and store the events that reached consensus
            final List<ConsensusRound> consensusRounds =
                    ConsensusUtils.applyEventsToConsensusUsingWrapper(eventEmitter, consensus, 1);

            // Apply all consensus events to ConsensusRoundHandler
            consensusRounds.forEach(
                    r -> {
                        roundConsumer.accept(r);
                        consensusRoundHandler.consensusRound(r);
                    });

            allConsensusRounds.addAll(consensusRounds);
        }

        // Allow some time to process the events
        assertEventuallyEquals(
                0,
                consensusRoundHandler::getRoundsInQueue,
                Duration.ofSeconds(5),
                "Consensus round queue was not drained");

        return allConsensusRounds;
    }

    /**
     * Submits transaction to {@link SwirldStateManager}. By definition, only self transactions can
     * be submitted.
     *
     * @param transactions the events whose transaction
     */
    private void submitSelfTransactions(final Iterable<ConsensusTransactionImpl> transactions) {
        transactions.forEach(
                tx ->
                        assertTrue(
                                swirldStateManager.submitTransaction(tx),
                                "Transaction was rejected by SwirldStateManager"));
    }
}

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
package com.swirlds.platform.test.event.source;

import static com.swirlds.platform.test.event.EventUtils.integerPowerDistribution;
import static com.swirlds.platform.test.event.EventUtils.staticDynamicValue;
import static com.swirlds.platform.test.event.RandomEventUtils.randomEventWithTimestamp;

import com.swirlds.common.test.TransactionGenerator;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.test.event.DynamicValue;
import com.swirlds.platform.test.event.DynamicValueGenerator;
import com.swirlds.platform.test.event.IndexedEvent;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Random;

/** A source of events. */
public abstract class AbstractEventSource<T extends AbstractEventSource<T>>
        implements EventSource<T> {

    /** The unique ID of this source/node. Is set by the StandardEventGenerator. */
    private int nodeId;

    /** Influences the probability that this node create a new event. */
    private DynamicValueGenerator<Double> newEventWeight;

    /** The amount of stake this node has. */
    private final long stake;

    /** The average size of a transaction, in bytes. */
    private static final double DEFAULT_AVG_TX_SIZE = 3;

    /** The standard deviation of the size of a transaction, in bytes. */
    private static final double DEFAULT_TX_SIZE_STD_DEV = 1;

    /** The average number of transactions. */
    private static final double DEFAULT_TX_COUNT_AVG = 3;

    /** The standard deviation of the number of transactions. */
    private static final double DEFAULT_TX_COUNT_STD_DEV = 3;

    /** The default amount of stake to allocate this node is no value is provided. */
    protected static final long DEFAULT_STAKE = 1;

    /** The default transaction generator used to create transaction for generated events. */
    protected static final TransactionGenerator DEFAULT_TRANSACTION_GENERATOR =
            r ->
                    TransactionUtils.randomSwirldTransactions(
                            r,
                            DEFAULT_AVG_TX_SIZE,
                            DEFAULT_TX_SIZE_STD_DEV,
                            DEFAULT_TX_COUNT_AVG,
                            DEFAULT_TX_COUNT_STD_DEV);

    /** The number of events that have been emitted by this event source. */
    private long eventCount;

    /**
     * A dynamic value generator that is used to determine the age of the other parent (for when we
     * ask another node for the other parent)
     */
    private DynamicValueGenerator<Integer> otherParentRequestIndex;

    /**
     * A dynamic value generator that is used to determine the age of the other parent (for when
     * another node is requesting the other parent from us)
     */
    private DynamicValueGenerator<Integer> otherParentProviderIndex;

    /** The number of recent events that this node is expected to store. */
    private int recentEventRetentionSize;

    /** Supplier of transactions for emitted events * */
    private TransactionGenerator transactionGenerator;

    private final boolean useFakeHashes;

    /**
     * Creates a new instance with the supplied transaction generator and stake.
     *
     * @param useFakeHashes indicates if fake hashes should be used instead of real ones
     * @param transactionGenerator a transaction generator to use when creating events
     * @param stake the stake allocated to this event source
     */
    protected AbstractEventSource(
            final boolean useFakeHashes,
            final TransactionGenerator transactionGenerator,
            final long stake) {
        this.useFakeHashes = useFakeHashes;
        this.transactionGenerator = transactionGenerator;
        this.stake = stake;
        nodeId = -1;
        setNewEventWeight(1.0);

        eventCount = 0;

        // with high probability, request the most recent event as an other parent to this node's
        // events
        otherParentRequestIndex = new DynamicValueGenerator<>(integerPowerDistribution(0.95));

        // initialize to always provide the most recent parent for nodes requesting an event as an
        // other parent
        otherParentProviderIndex = new DynamicValueGenerator<>(staticDynamicValue(0));

        recentEventRetentionSize = 100;
    }

    protected AbstractEventSource(final AbstractEventSource<T> that) {
        this.useFakeHashes = that.useFakeHashes;
        this.transactionGenerator = that.transactionGenerator;
        this.stake = that.stake;
        this.nodeId = that.nodeId;
        this.newEventWeight = that.newEventWeight.cleanCopy();

        this.eventCount = 0;
        this.otherParentRequestIndex = that.otherParentRequestIndex.cleanCopy();
        this.otherParentProviderIndex = that.otherParentProviderIndex.cleanCopy();
        this.recentEventRetentionSize = that.recentEventRetentionSize;
    }

    /**
     * Maintain the maximum size of a list of events by removing the last element (if needed).
     * Utility method that is useful for child classes.
     */
    protected void pruneEventList(final LinkedList<IndexedEvent> events) {
        if (events.size() > getRecentEventRetentionSize()) {
            events.removeLast();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        eventCount = 0;
    }

    /** {@inheritDoc} */
    @Override
    public int getNodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public T setNodeId(final int nodeId) {
        this.nodeId = nodeId;
        return (T) this;
    }

    @Override
    public long getStake() {
        return stake;
    }

    /** {@inheritDoc} */
    @Override
    public double getNewEventWeight(final Random random, final long eventIndex) {
        return newEventWeight.get(random, eventIndex);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public T setNewEventWeight(final DynamicValue<Double> dynamicWeight) {
        this.newEventWeight = new DynamicValueGenerator<>(dynamicWeight);
        return (T) this;
    }

    /** {@inheritDoc} */
    @Override
    public IndexedEvent generateEvent(
            final Random random,
            final long eventIndex,
            final EventSource<?> otherParent,
            final Instant timestamp) {
        final IndexedEvent event;

        // The higher the index, the older the event. Use the oldest parent between the provided and
        // requested value.
        final int otherParentIndex =
                Math.max(
                        // event index (event age) that this node wants to use as it's other parent
                        getRequestedOtherParentAge(random, eventIndex),
                        // event index (event age) that the other node wants to provide as an other
                        // parent to this node
                        otherParent.getProvidedOtherParentAge(random, eventIndex));

        final IndexedEvent otherParentEvent = otherParent.getRecentEvent(random, otherParentIndex);

        event =
                randomEventWithTimestamp(
                        random,
                        nodeId,
                        timestamp,
                        transactionGenerator.generate(random),
                        getLatestEvent(random),
                        otherParentEvent,
                        useFakeHashes);

        eventCount++;

        // Although we could just set latestEvent directly, calling this method allows for dishonest
        // implementations
        // to override standard behavior.
        setLatestEvent(random, event);

        return event;
    }

    /**
     * {@inheritDoc}
     *
     * @param transactionGenerator
     */
    public T setTransactionGenerator(final TransactionGenerator transactionGenerator) {
        this.transactionGenerator = transactionGenerator;
        return (T) this;
    }

    /** {@inheritDoc} */
    @Override
    public int getRequestedOtherParentAge(final Random random, final long eventIndex) {
        return otherParentRequestIndex.get(random, eventIndex);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public T setRequestedOtherParentAgeDistribution(final DynamicValue<Integer> otherParentIndex) {
        otherParentRequestIndex = new DynamicValueGenerator<>(otherParentIndex);
        return (T) this;
    }

    /** {@inheritDoc} */
    @Override
    public int getProvidedOtherParentAge(final Random random, final long eventIndex) {
        return otherParentProviderIndex.get(random, eventIndex);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public T setProvidedOtherParentAgeDistribution(final DynamicValue<Integer> otherParentIndex) {
        this.otherParentProviderIndex = new DynamicValueGenerator<>(otherParentIndex);
        return (T) this;
    }

    /** {@inheritDoc} */
    @Override
    public int getRecentEventRetentionSize() {
        return recentEventRetentionSize;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public T setRecentEventRetentionSize(final int recentEventRetentionSize) {
        this.recentEventRetentionSize = recentEventRetentionSize;
        return (T) this;
    }
}

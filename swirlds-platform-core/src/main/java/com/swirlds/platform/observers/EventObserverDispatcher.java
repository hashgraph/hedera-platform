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
package com.swirlds.platform.observers;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * A type which accretes observers of several different types. This type facilitates the implied
 * observer DAG.
 */
public class EventObserverDispatcher
        implements EventReceivedObserver,
                PreConsensusEventObserver,
                EventAddedObserver,
                ConsensusRoundObserver,
                StaleEventObserver {

    /** A list of implementors of the {@link EventReceivedObserver} interface */
    private final List<EventReceivedObserver> eventReceivedObservers;
    /** A list of implementors of the {@link PreConsensusEventObserver} interface */
    private final List<PreConsensusEventObserver> preConsensusEventObservers;
    /** A list of implementors of the {@link EventAddedObserver} interface */
    private final List<EventAddedObserver> eventAddedObservers;
    /** A list of implementors of the {@link ConsensusRoundObserver} interface */
    private final List<ConsensusRoundObserver> consensusRoundObservers;
    /** A list of implementors of the {@link StaleEventObserver} interface */
    private final List<StaleEventObserver> staleEventObservers;

    /**
     * Constructor
     *
     * @param observers a list of {@link EventObserver} implementors
     */
    public EventObserverDispatcher(final List<EventObserver> observers) {
        eventReceivedObservers = new ArrayList<>();
        preConsensusEventObservers = new ArrayList<>();
        eventAddedObservers = new ArrayList<>();
        consensusRoundObservers = new ArrayList<>();
        staleEventObservers = new ArrayList<>();

        for (final EventObserver observer : observers) {
            addObserver(observer);
        }
    }

    /**
     * Constructor
     *
     * @param observers a variadic sequence of {@link EventObserver} implementors
     */
    public EventObserverDispatcher(final EventObserver... observers) {
        this(List.of(observers));
    }

    /**
     * Adds an observer
     *
     * @param observer the observer to add
     */
    public void addObserver(final EventObserver observer) {
        if (observer instanceof EventReceivedObserver o) {
            eventReceivedObservers.add(o);
        }
        if (observer instanceof PreConsensusEventObserver o) {
            preConsensusEventObservers.add(o);
        }
        if (observer instanceof EventAddedObserver o) {
            eventAddedObservers.add(o);
        }
        if (observer instanceof ConsensusRoundObserver o) {
            consensusRoundObservers.add(o);
        }
        if (observer instanceof StaleEventObserver o) {
            staleEventObservers.add(o);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void receivedEvent(final GossipEvent event) {
        for (final EventReceivedObserver observer : eventReceivedObservers) {
            observer.receivedEvent(event);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void preConsensusEvent(final EventImpl event) {
        for (final PreConsensusEventObserver observer : preConsensusEventObservers) {
            observer.preConsensusEvent(event);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void eventAdded(final EventImpl event) {
        for (final EventAddedObserver observer : eventAddedObservers) {
            observer.eventAdded(event);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        for (final ConsensusRoundObserver observer : consensusRoundObservers) {
            observer.consensusRound(consensusRound);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void staleEvent(final EventImpl event) {
        for (final StaleEventObserver observer : staleEventObservers) {
            observer.staleEvent(event);
        }
    }
}

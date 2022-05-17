/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is owned by Hedera Hashgraph, LLC, which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.observers;

import com.swirlds.platform.ConsensusRound;
import com.swirlds.platform.EventImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * A type which accretes observers of several different types. This type facilitates
 * the implied observer DAG.
 */
public class EventObserverDispatcher implements
		ConsensusRoundObserver,
		EventAddedObserver,
		PreConsensusEventObserver,
		StaleEventObserver{

	/**
	 * A list of implementors of the {@link EventAddedObserver} interface
	 */
	private final List<EventAddedObserver> eventAddedObservers;

	/**
	 * A list of implementors of the {@link PreConsensusEventObserver} interface
	 */
	private final List<PreConsensusEventObserver> preConsensusEventObservers;

	/**
	 * A list of implementors of the {@link StaleEventObserver} interface
	 */
	private final List<StaleEventObserver> staleEventObservers;

	/**
	 * A list of implementors of the {@link ConsensusRoundObserver} interface
	 */
	private final List<ConsensusRoundObserver> consensusRoundObservers;

	/**
	 * Constructor
	 *
	 * @param observers
	 * 		a list of {@link EventObserver} implementors
	 */
	public EventObserverDispatcher(final List<EventObserver> observers) {
		eventAddedObservers = new ArrayList<>();
		preConsensusEventObservers = new ArrayList<>();
		staleEventObservers = new ArrayList<>();
		consensusRoundObservers = new ArrayList<>();

		for (final EventObserver observer : observers) {
			addObserver(observer);
		}
	}

	/**
	 * Constructor
	 *
	 * @param observers
	 * 		a variadic sequence of {@link EventObserver} implementors
	 */
	public EventObserverDispatcher(final EventObserver... observers) {
		this(List.of(observers));
	}

	/**
	 * Adds an observer
	 *
	 * @param observer
	 * 		the observer to add
	 */
	public void addObserver(final EventObserver observer) {
		if (observer instanceof EventAddedObserver o) {
			eventAddedObservers.add(o);
		}
		if (observer instanceof PreConsensusEventObserver o) {
			preConsensusEventObservers.add(o);
		}
		if (observer instanceof StaleEventObserver o) {
			staleEventObservers.add(o);
		}
		if (observer instanceof ConsensusRoundObserver o) {
			consensusRoundObservers.add(o);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void consensusRound(final ConsensusRound consensusRound) {
		for (final ConsensusRoundObserver observer : consensusRoundObservers) {
			observer.consensusRound(consensusRound);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void eventAdded(final EventImpl event) {
		for (final EventAddedObserver observer : eventAddedObservers) {
			observer.eventAdded(event);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preConsensusEvent(final EventImpl event) {
		for (final PreConsensusEventObserver observer : preConsensusEventObservers) {
			observer.preConsensusEvent(event);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void staleEvent(final EventImpl event) {
		for (final StaleEventObserver observer : staleEventObservers) {
			observer.staleEvent(event);
		}
	}
}

/*
 * (c) 2016-2022 Swirlds, Inc.
 *
 * This software is owned by Swirlds, Inc., which retains title to the software. This software is protected by various
 * intellectual property laws throughout the world, including copyright and patent laws. This software is licensed and
 * not sold. You must use this software only in accordance with the terms of the Hashgraph Open Review license at
 *
 * https://github.com/hashgraph/swirlds-open-review/raw/master/LICENSE.md
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THIS SOFTWARE, EITHER EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * OR NON-INFRINGEMENT.
 */

package com.swirlds.platform.observers;

import com.swirlds.platform.EventImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * A type which accretes observers of several different types. This type facilitates
 * the implied observer DAG.
 */
public class EventObserverDispatcher implements
		ConsensusEventObserver,
		EventAddedObserver,
		PreConsensusEventObserver,
		StaleEventObserver {

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
	 * A list of implementors of the {@link ConsensusEventObserver} interface
	 */
	private final List<ConsensusEventObserver> consensusEventObservers;

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
		consensusEventObservers = new ArrayList<>();

		for (final EventObserver observer : observers) {
			if (observer instanceof EventAddedObserver) {
				eventAddedObservers.add((EventAddedObserver) observer);
			}
			if (observer instanceof PreConsensusEventObserver) {
				preConsensusEventObservers.add((PreConsensusEventObserver) observer);
			}
			if (observer instanceof StaleEventObserver) {
				staleEventObservers.add((StaleEventObserver) observer);
			}
			if (observer instanceof ConsensusEventObserver) {
				consensusEventObservers.add((ConsensusEventObserver) observer);
			}
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
	 * {@inheritDoc}
	 */
	@Override
	public void consensusEvent(final EventImpl event) {
		for (final ConsensusEventObserver observer : consensusEventObservers) {
			observer.consensusEvent(event);
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

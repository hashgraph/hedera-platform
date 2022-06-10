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

package com.swirlds.platform.state;

import com.swirlds.common.Releasable;
import com.swirlds.platform.EventImpl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** hold a SwirldState along with some information about it */
public class StateInfo implements Releasable {
	/** the SwirldState root that this object describes */
	private final AtomicReference<State> state = new AtomicReference<>();
	/**
	 * lastCons is a consensus event whose transactions have already been handled by state, as have all
	 * consensus events before it in the consensus order.
	 */
	private volatile EventImpl lastCons;
	/** have we called "freeze" on the SwirldState, promising it there will be no more transactions? */
	private volatile boolean frozen = false;

	/**
	 * pass in everything that is stored in the StateInfo
	 *
	 * @param state
	 * 		the state to feed transactions to
	 * @param lastCons
	 * 		the last consensus event, in consensus order
	 * @param frozen
	 * 		has state.freeze() been called yet, to promise never to send another transaction?
	 */
	public StateInfo(final State state, final EventImpl lastCons, final boolean frozen) {
		this.initializeState(state);
		this.setLastCons(lastCons);
		this.setFrozen(frozen);
	}

	private StateInfo(final StateInfo stateToCopy) {
		this.initializeState(stateToCopy.getState().copy());
		this.setLastCons(stateToCopy.getLastCons());
		this.setFrozen(stateToCopy.isFrozen());
	}

	/**
	 * make a copy of this StateInfo, where it also makes a new copy of the state
	 *
	 * @return a copy of this StateInfo
	 */
	public StateInfo copy() {
		return new StateInfo(this);
	}

	@Override
	public void release() {
		state.get().decrementReferenceCount();
	}

	public State getState() {
		return state.get();
	}

	private void initializeState(final State newState) {
		final State currState = state.get();
		if (currState != null) {
			currState.decrementReferenceCount();
		}
		newState.incrementReferenceCount();
		this.state.set(newState);
	}

	public void setState(final State state) {
		this.state.set(state);
	}

	public EventImpl getLastCons() {
		return lastCons;
	}

	public void setLastCons(final EventImpl lastCons) {
		this.lastCons = lastCons;
	}

	/**
	 * Updates the state object using the given {@code updateFunction}. This method uses {@link
	 * AtomicReference#getAndUpdate(UnaryOperator)} to update the state, so the update function must not have any side
	 * effects.
	 *
	 * @param updateFunction
	 * 		the side effect free update function to apply to the state
	 */
	protected void updateState(final UnaryOperator<State> updateFunction) {
		state.getAndUpdate(updateFunction);
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(final boolean frozen) {
		this.frozen = frozen;
	}
}

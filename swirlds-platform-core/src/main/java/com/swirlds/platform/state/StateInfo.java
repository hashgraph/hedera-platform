/*
 * (c) 2016-2020 Swirlds, Inc.
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

package com.swirlds.platform.state;

import com.swirlds.common.Releasable;
import com.swirlds.common.SwirldState;
import com.swirlds.platform.EventImpl;

/** hold a SwirldState along with some information about it */
public class StateInfo implements Releasable {
	/** the SwirldState that this object describes */
	private SwirldState state;
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
	public StateInfo(SwirldState state, EventImpl lastCons, boolean frozen) {
		this.setState(state);
		this.setLastCons(lastCons);
		this.setFrozen(frozen);
	}

	private StateInfo(final StateInfo stateToCopy) {
		this.setState(stateToCopy.getState().copy());
		this.setLastCons(stateToCopy.getLastCons());
		this.setFrozen(stateToCopy.isFrozen());
	}

	/**
	 * make a copy of this StateInfo, where it also makes a new copy of the state
	 */
	public StateInfo copy() {
		return new StateInfo(this);
	}

	@Override
	public void release() {
		getState().decrementReferenceCount();
	}

	public SwirldState getState() {
		return state;
	}

	public void setState(SwirldState state) {
		if (this.state != null) {
			this.state.decrementReferenceCount();
		}
		this.state = state;
		state.incrementReferenceCount();
	}

	public EventImpl getLastCons() {
		return lastCons;
	}

	public void setLastCons(EventImpl lastCons) {
		this.lastCons = lastCons;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
	}
}

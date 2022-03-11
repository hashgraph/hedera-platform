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

package com.swirlds.virtualmap.internal.merkle;

import com.swirlds.virtualmap.internal.StateAccessor;

import java.util.Objects;

/**
 * Basic implementation of {@link StateAccessor} which delegates to a {@link VirtualMapState}.
 * Initially this separation of interface and implementation class was essential to the design.
 * At this point, it is just useful for testing purposes.
 */
public final class StateAccessorImpl implements StateAccessor {
	/**
	 * The state. Cannot be null.
	 */
	private final VirtualMapState state;

	/**
	 * Create a new {@link StateAccessorImpl}.
	 *
	 * @param state
	 * 		The state. Cannot be null.
	 */
	public StateAccessorImpl(VirtualMapState state) {
		this.state = Objects.requireNonNull(state);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getLabel() {
		return state.getLabel();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getFirstLeafPath() {
		return state.getFirstLeafPath();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLastLeafPath() {
		return state.getLastLeafPath();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setFirstLeafPath(final long path) {
		state.setFirstLeafPath(path);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setLastLeafPath(final long path) {
		state.setLastLeafPath(path);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long size() {
		return state.getSize();
	}
}

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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.virtualmap.internal.StateAccessor;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;

public class ReconnectState implements StateAccessor {
	private long firstLeafPath;
	private long lastLeafPath;

	public ReconnectState(long firstLeafPath, long lastLeafPath) {
		this.firstLeafPath = firstLeafPath;
		this.lastLeafPath = lastLeafPath;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getLabel() {
		throw new UnsupportedOperationException("Not called");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getFirstLeafPath() {
		return firstLeafPath;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getLastLeafPath() {
		return lastLeafPath;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setFirstLeafPath(final long path) {
		this.firstLeafPath = path;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setLastLeafPath(final long path) {
		this.lastLeafPath = path;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long size() {
		return lastLeafPath == INVALID_PATH ? 0 : (lastLeafPath - firstLeafPath + 1);
	}
}

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

package com.swirlds.platform.stats;

import com.swirlds.common.statistics.StatEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates a value and expects {@link #update()} to be called on it once a second in order to track a per second
 * average
 */
public class PerSecondStat {
	final AtomicLong accumulator;
	final AverageStat average;

	public PerSecondStat(final AverageStat average) {
		this.accumulator = new AtomicLong(0);
		this.average = average;
	}

	public void increment() {
		accumulator.incrementAndGet();
	}

	public void update() {
		average.update(accumulator.getAndSet(0));
	}

	public List<StatEntry> getStats() {
		return List.of(average.getStatEntry());
	}
}

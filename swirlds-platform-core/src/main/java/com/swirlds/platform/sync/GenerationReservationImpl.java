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

package com.swirlds.platform.sync;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple implementation of {@link GenerationReservation}.
 */
public final class GenerationReservationImpl implements GenerationReservation {

	/**
	 * The event generation that is reserved
	 */
	private final long generation;

	/**
	 * The number of reservations on this generation
	 */
	private final AtomicInteger numReservations;

	public GenerationReservationImpl(final long generation) {
		this.generation = generation;
		numReservations = new AtomicInteger(1);
	}

	/**
	 * Increments the number of reservations on this generation.
	 */
	public void incrementReservations() {
		numReservations.incrementAndGet();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getNumReservations() {
		return numReservations.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		numReservations.decrementAndGet();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getGeneration() {
		return generation;
	}
}

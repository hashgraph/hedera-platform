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

package com.swirlds.common.threading;

/**
 * <p>
 * Similar semantics to {@link java.util.concurrent.atomic.AtomicInteger AtomicInteger}, but for a double.
 * </p>
 *
 * <p>
 * This implementation uses locks to provide thread safety, and as a result may not be as performant
 * as the java built-in family of atomic objects.
 * </p>
 */
public class AtomicDouble {

	private double value;

	/**
	 * Create a new AtomicDouble with an initial value of 0.0.
	 */
	public AtomicDouble() {

	}

	/**
	 * Create a new AtomicDouble with an initial value.
	 *
	 * @param initialValue
	 * 		the initial value held by the double
	 */
	public AtomicDouble(final double initialValue) {
		value = initialValue;
	}

	/**
	 * Get the value.
	 */
	public synchronized double get() {
		return value;
	}

	/**
	 * Set the value.
	 *
	 * @param value
	 * 		the value to set
	 */
	public synchronized void set(final double value) {
		this.value = value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized String toString() {
		return Double.toString(value);
	}
}

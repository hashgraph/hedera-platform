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

package com.swirlds.common.utility;

/**
 * A utility class for passing a reference to an object.
 * Cheaper than using an atomic reference when atomicity is not needed.
 *
 * @param <V>
 * 		the type of the value being passed
 */
public class ValueReference<V> {

	private V value;

	/**
	 * Create a new ValueReference with an initial value of null.
	 */
	public ValueReference() {

	}

	/**
	 * Create a new ValueReference with an initial value.
	 *
	 * @param value
	 * 		the initial value
	 */
	public ValueReference(final V value) {
		this.value = value;
	}

	/**
	 * Get the value.
	 */
	public V getValue() {
		return value;
	}

	/**
	 * Set the value.
	 */
	public void setValue(final V value) {
		this.value = value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "[" + (value == null ? null : value) + "]";
	}
}

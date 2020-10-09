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

package com.swirlds.fchashmap.internal;

/**
 * Represents a single modification of a FCHashMap.
 */
public class Mutation<V> {

	/**
	 * The copy version when this mutation was performed.
	 */
	public final long version;

	/**
	 * The value after the mutation.
	 */
	public V value;

	/**
	 * Differentiates between a mutation that is null because it was deleted
	 * and a mutation that is null because the user has set the value to null.
	 */
	public boolean deleted;

	public Mutation(final long version, V value, boolean deleted) {
		this.version = version;
		this.value = value;
		this.deleted = deleted;
	}

	/**
	 * Convert this mutation to a human readable string. For debugging purposes.
	 */
	public String toString() {
		return "(" + version + ": " + (deleted ? "DELETED" : value) + ")";
	}
}

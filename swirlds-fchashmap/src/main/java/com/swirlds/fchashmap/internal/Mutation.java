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

package com.swirlds.fchashmap.internal;

/**
 * Represents a single modification of a FCHashMap. Can be assembled into a linked list.
 */
public class Mutation<V> {

	/**
	 * The copy version when this mutation was performed.
	 */
	private final long version;

	/**
	 * The value after the mutation. A value of null signifies a deletion.
	 */
	private V value;

	/**
	 * The previous mutation in the linked list, or null if this mutation is the oldest unreleased mutation.
	 */
	private Mutation<V> previous;

	/**
	 * Create a mutation with a version, value, and previous mutation.
	 *
	 * @param version
	 * 		the version of the mutation
	 * @param value
	 * 		the value of the mutation
	 * @param previous
	 * 		the previous mutation
	 */
	public Mutation(final long version, final V value, final Mutation<V> previous) {
		this.version = version;
		this.value = value;
		this.previous = previous;
	}

	/**
	 * Convert this mutation to a human-readable string. For debugging purposes.
	 */
	public String toString() {
		return "(version = " + version +
				", value = " + (value == null ? "DELETED" : value) + ")";
	}

	/**
	 * Get the value held by the mutation. If null then this mutation signifies a deletion.
	 *
	 * @return the value held by this mutation
	 */
	public V getValue() {
		return value;
	}

	/**
	 * Set the value held by this mutation.
	 *
	 * @param value
	 * 		the value of the mutation, or null if this mutation should signify a deletion
	 */
	public void setValue(final V value) {
		this.value = value;
	}

	/**
	 * Get the version of the {@link com.swirlds.fchashmap.FCHashMap FCHashMap} when this mutation was created.
	 *
	 * @return the version of the mutation
	 */
	public long getVersion() {
		return version;
	}

	/**
	 * Get the mutation before this mutation, or null if this mutation is the oldest mutation
	 *
	 * @return the next mutation
	 */
	public Mutation<V> getPrevious() {
		return previous;
	}

	/**
	 * Set the previous mutation before this mutation.
	 *
	 * @param previous
	 * 		the next mutation
	 */
	public void setPrevious(final Mutation<V> previous) {
		this.previous = previous;
	}
}
